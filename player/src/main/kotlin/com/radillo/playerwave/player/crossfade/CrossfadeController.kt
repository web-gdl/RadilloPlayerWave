package com.radillo.playerwave.player.crossfade

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.radillo.playerwave.core.diagnostics.EventLog
import com.radillo.playerwave.core.settings.PlaybackSettings
import com.radillo.playerwave.player.audiotap.RadilloRenderersFactory

/**
 * Crossfade: unos segundos antes del final, la siguiente canción entra subiendo de volumen
 * mientras la actual baja, sin silencio entre ellas.
 *
 * Cómo, sin tocar la cola ni la sesión multimedia:
 * - El reproductor **principal** (el de la sesión, el volante, la notificación y el visualizador)
 *   salta a la siguiente canción al empezar la mezcla y sube de 0 al volumen que tenía.
 * - Un reproductor **auxiliar** efímero, preparado un momento antes, toca el final de la canción
 *   que se va y baja hasta 0; luego se libera. Comparte la sesión de audio del principal, así que
 *   el ecualizador también se le aplica. Nunca pide el foco de audio.
 *
 * **Al cambiar de canción a mano** (siguiente, anterior, elegir en la cola), si el ajuste lo permite,
 * se hace una mezcla corta ([CrossfadePlan.manualFadeMs]). El salto lo intercepta
 * [SkipCrossfadePlayer] y llega aquí por [requestSkip] **antes** de ocurrir: la canción actual sigue
 * sonando entera mientras el auxiliar se prepara con ella un poco más adelante; cuando el principal
 * llega a ese punto, el auxiliar sigue con ella (bajando) y el principal salta y sube. Así nunca hay
 * silencio (antes el principal saltaba primero y, mientras el auxiliar cargaba, no sonaba nada).
 *
 * Se cancela al pausar. Si otro (atenuación del navegador, foco de audio) cambia el volumen en plena
 * mezcla, se le deja el volumen a él. Todo en el hilo principal.
 */
@OptIn(UnstableApi::class)
internal class CrossfadeController(
    private val context: Context,
    private val player: ExoPlayer,
    private val audioAttributes: AudioAttributes,
) : Player.Listener {

    private enum class Phase { IDLE, PREPARING, SKIP_PENDING, FADING }

    private val handler = Handler(Looper.getMainLooper())
    private val prefs = PlaybackSettings.prefs(context)
    private var fadeMs = PlaybackSettings.crossfadeSeconds(context) * 1000L
    private var onSkip = PlaybackSettings.crossfadeOnSkip(context)
    /** Duración de la mezcla en curso (completa al final de una canción, corta al saltar). */
    private var activeFadeMs = fadeMs

    /** Salto a mano esperando a que el auxiliar esté listo (se ejecuta al empezar la mezcla). */
    private var pendingSkip: (() -> Unit)? = null
    private var skipRequestedAt = 0L

    private var phase = Phase.IDLE
    private var tail: ExoPlayer? = null
    /** Canción que se está despidiendo (para detectar un salto del usuario mientras se prepara). */
    private var outgoingUid: Any? = null
    private var tailStartMs = 0L
    private var fadeStartedAt = 0L
    private var targetVolume = 1f
    private var lastSetVolume = -1f

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PlaybackSettings.KEY_CROSSFADE_SECONDS || key == PlaybackSettings.KEY_CROSSFADE_ON_SKIP) {
            fadeMs = PlaybackSettings.crossfadeSeconds(context) * 1000L
            onSkip = PlaybackSettings.crossfadeOnSkip(context)
            if (fadeMs == 0L) cancel()
            schedule()
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            step()
            if (phase != Phase.IDLE || (fadeMs > 0 && player.isPlaying)) {
                handler.postDelayed(this, if (phase == Phase.IDLE) IDLE_TICK_MS else ACTIVE_TICK_MS)
            }
        }
    }

    init {
        player.addListener(this)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        schedule()
    }

    fun release() {
        cancel(runPendingSkip = false)
        handler.removeCallbacks(tick)
        player.removeListener(this)
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    // ---- Eventos del reproductor principal ----

    override fun onIsPlayingChanged(isPlaying: Boolean) = schedule()

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        // Una pausa en plena mezcla la corta: al reanudar suena solo la canción nueva. Un salto
        // pendiente se hace igual (el usuario lo pidió), sin mezcla.
        if (!playWhenReady) cancel()
        schedule()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // Un salto del usuario mientras se preparaba la mezcla la deja sin sentido.
        if (phase == Phase.PREPARING) cancel()
        // La canción cambió por otro lado (terminó, se cambió la cola): el salto pendiente ya no
        // corresponde; repetirlo saltaría dos veces.
        if (phase == Phase.SKIP_PENDING) cancel(runPendingSkip = false)
    }

    /**
     * Salto a mano (siguiente, anterior, elegir en la cola). Devuelve `true` si se encarga de él:
     * [skip] se ejecutará en unas décimas, al empezar la mezcla (o sin mezcla si no se puede).
     * Devuelve `false` si hay que saltar ya, sin mezcla.
     */
    fun requestSkip(skip: () -> Unit): Boolean {
        when (phase) {
            Phase.FADING -> return false
            // Pulsaciones seguidas: el salto anterior se hace ya y este también, sin mezcla.
            Phase.SKIP_PENDING -> {
                cancel()
                return false
            }
            Phase.PREPARING -> cancel()
            Phase.IDLE -> Unit
        }
        if (fadeMs <= 0 || !onSkip || !player.isPlaying) return false
        val item = player.currentMediaItem ?: return false
        val position = player.currentPosition
        if (!CrossfadePlan.skipEligible(position, player.duration.takeIf { it != C.TIME_UNSET } ?: 0L)) return false
        tailStartMs = position + CrossfadePlan.SKIP_LEAD_MS
        tail = prepareTail(item, tailStartMs, "no se pudo mezclar el salto")
        if (tail == null) return false
        outgoingUid = player.currentPeriodUid()
        pendingSkip = skip
        skipRequestedAt = SystemClock.elapsedRealtime()
        phase = Phase.SKIP_PENDING
        schedule()
        return true
    }

    private fun schedule() {
        handler.removeCallbacks(tick)
        if (phase != Phase.IDLE || (fadeMs > 0 && player.isPlaying)) handler.post(tick)
    }

    // ---- Máquina de estados ----

    private fun step() {
        when (phase) {
            Phase.IDLE -> maybePrepare()
            Phase.PREPARING -> maybeStart()
            Phase.SKIP_PENDING -> maybeStartSkip()
            Phase.FADING -> ramp()
        }
    }

    private fun maybePrepare() {
        if (fadeMs <= 0 || !player.isPlaying) return
        val item = player.currentMediaItem ?: return
        val duration = player.duration
        if (duration == C.TIME_UNSET) return
        val eligible = CrossfadePlan.eligible(
            fadeMs = fadeMs,
            durationMs = duration,
            hasNext = player.nextMediaItemIndex != C.INDEX_UNSET,
            repeatOne = player.repeatMode == Player.REPEAT_MODE_ONE,
        )
        if (!eligible || !CrossfadePlan.shouldPrepare(player.currentPosition, duration, fadeMs)) return
        tailStartMs = CrossfadePlan.startAt(duration, fadeMs)
        outgoingUid = player.currentPeriodUid()
        tail = prepareTail(item, tailStartMs, "no se pudo preparar la mezcla")
        if (tail != null) phase = Phase.PREPARING
    }

    private fun maybeStart() {
        val aux = tail ?: return cancel()
        if (player.currentPeriodUid() != outgoingUid || !player.isPlaying) return cancel()
        val position = player.currentPosition
        if (position < tailStartMs - ALIGN_TOLERANCE_MS) return
        if (aux.playbackState != Player.STATE_READY) {
            // Sin auxiliar listo a tiempo: transición normal, sin mezcla.
            if (position > tailStartMs + CrossfadePlan.START_GRACE_MS) cancel()
            return
        }
        val next = player.nextMediaItemIndex
        if (next == C.INDEX_UNSET) return cancel()
        targetVolume = player.volume
        aux.volume = targetVolume
        // La fase cambia antes del salto: el aviso de cambio de canción llega dentro de seek y no
        // debe tomarse por un salto del usuario.
        phase = Phase.FADING
        activeFadeMs = fadeMs
        fadeStartedAt = SystemClock.elapsedRealtime()
        aux.play()
        setMainVolume(0f)
        player.seekToDefaultPosition(next)
    }

    private fun ramp() {
        val aux = tail ?: return finish(restore = true)
        if (lastSetVolume >= 0f && player.volume != lastSetVolume) {
            // Otro (atenuación del navegador, foco de audio) tomó el volumen: que mande él.
            return finish(restore = false)
        }
        val progress = (SystemClock.elapsedRealtime() - fadeStartedAt).toFloat() / activeFadeMs
        val (incoming, outgoing) = CrossfadePlan.gains(progress)
        setMainVolume(targetVolume * incoming)
        aux.volume = targetVolume * outgoing
        if (progress >= 1f || aux.playbackState == Player.STATE_ENDED) finish(restore = true)
    }

    /** Salto a mano pendiente: arranca la mezcla en cuanto el auxiliar está listo y alineado. */
    private fun maybeStartSkip() {
        val aux = tail ?: return cancel()
        if (player.currentPeriodUid() != outgoingUid) return cancel(runPendingSkip = false)
        val waited = SystemClock.elapsedRealtime() - skipRequestedAt
        val decision = CrossfadePlan.skipDecision(
            ready = aux.playbackState == Player.STATE_READY,
            positionMs = player.currentPosition,
            tailStartMs = tailStartMs,
            waitedMs = waited,
        )
        when (decision) {
            CrossfadePlan.Skip.WAIT -> Unit
            CrossfadePlan.Skip.GIVE_UP -> {
                EventLog.log("Crossfade: la mezcla del salto no estuvo lista a tiempo; salto normal")
                cancel()
            }
            CrossfadePlan.Skip.START -> {
                val skip = pendingSkip
                pendingSkip = null
                targetVolume = player.volume
                aux.volume = targetVolume
                // El auxiliar sigue con la canción que se va justo donde va el principal; luego el
                // principal salta (en silencio) y sube. La fase cambia antes del salto: el aviso de
                // cambio de canción llega dentro de él y no debe cancelar nada.
                phase = Phase.FADING
                activeFadeMs = CrossfadePlan.manualFadeMs(fadeMs)
                fadeStartedAt = SystemClock.elapsedRealtime()
                aux.play()
                setMainVolume(0f)
                outgoingUid = null
                skip?.invoke()
            }
        }
    }

    private fun finish(restore: Boolean) {
        releaseTail()
        if (restore) player.volume = targetVolume
        lastSetVolume = -1f
        phase = Phase.IDLE
    }

    /**
     * Corta la mezcla en curso (o la preparación) y deja el volumen como estaba. Un salto a mano
     * pendiente se hace en el acto, sin mezcla, salvo que [runPendingSkip] sea `false`.
     */
    private fun cancel(runPendingSkip: Boolean = true) {
        val wasFading = phase == Phase.FADING
        val skip = pendingSkip
        pendingSkip = null
        releaseTail()
        if (wasFading && lastSetVolume >= 0f && player.volume == lastSetVolume) player.volume = targetVolume
        lastSetVolume = -1f
        phase = Phase.IDLE
        if (runPendingSkip) skip?.invoke()
    }

    private fun setMainVolume(volume: Float) {
        player.volume = volume
        // Se lee de vuelta: el reproductor puede redondear y la comparación debe ser exacta.
        lastSetVolume = player.volume
    }

    private fun releaseTail() {
        tail?.let { runCatching { it.release() } }
        tail = null
        outgoingUid = null
    }

    private fun Player.currentPeriodUid(): Any? {
        val timeline = currentTimeline
        if (timeline.isEmpty) return null
        return timeline.getUidOfPeriod(currentPeriodIndex)
    }

    /**
     * Auxiliar preparado (en pausa) con [item] desde [startMs], o `null` si no se pudo; en ese caso
     * se libera lo que se hubiera creado y se apunta [failure] en el registro.
     */
    private fun prepareTail(item: MediaItem, startMs: Long, failure: String): ExoPlayer? {
        var aux: ExoPlayer? = null
        return try {
            buildTail().also { aux = it }.apply {
                setMediaItem(item, startMs)
                playWhenReady = false
                prepare()
            }
        } catch (e: Exception) {
            aux?.let { runCatching { it.release() } }
            EventLog.log("Crossfade: $failure (${e.javaClass.simpleName})")
            null
        }
    }

    /** Reproductor mínimo: solo audio, búfer pequeño, sin tap del visualizador ni foco de audio. */
    private fun buildTail(): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(2_500, 15_000, 500, 1_000)
            .setTargetBufferBytes(1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
        return ExoPlayer.Builder(context, RadilloRenderersFactory(context, tap = null))
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ false)
            .setHandleAudioBecomingNoisy(false)
            .build()
            .also { aux ->
                // Misma sesión de audio: el ecualizador del dispositivo también se le aplica.
                val session = player.audioSessionId
                if (session != C.AUDIO_SESSION_ID_UNSET) aux.audioSessionId = session
            }
    }

    private companion object {
        const val IDLE_TICK_MS = 250L
        const val ACTIVE_TICK_MS = 40L
        const val ALIGN_TOLERANCE_MS = CrossfadePlan.ALIGN_TOLERANCE_MS
    }
}
