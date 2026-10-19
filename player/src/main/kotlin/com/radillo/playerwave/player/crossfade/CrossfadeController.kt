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
 * se hace una mezcla corta ([CrossfadePlan.manualFadeMs]): la nueva entra desde 0 al instante y el
 * auxiliar retoma la anterior donde iba y la apaga.
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

    private enum class Phase { IDLE, PREPARING, FADING }

    private val handler = Handler(Looper.getMainLooper())
    private val prefs = PlaybackSettings.prefs(context)
    private var fadeMs = PlaybackSettings.crossfadeSeconds(context) * 1000L
    private var onSkip = PlaybackSettings.crossfadeOnSkip(context)
    /** Duración de la mezcla en curso (completa al final de una canción, corta al saltar). */
    private var activeFadeMs = fadeMs

    /** Última canción y posición vistas sonando: lo que hay que retomar si el usuario salta. */
    private var lastItem: MediaItem? = null
    private var lastPositionMs = 0L
    private var lastSeenAt = 0L

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
        cancel()
        handler.removeCallbacks(tick)
        player.removeListener(this)
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    // ---- Eventos del reproductor principal ----

    override fun onIsPlayingChanged(isPlaying: Boolean) = schedule()

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        // Una pausa en plena mezcla la corta: al reanudar suena solo la canción nueva.
        if (!playWhenReady) cancel()
        schedule()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // Un salto del usuario mientras se preparaba la mezcla la deja sin sentido.
        if (phase == Phase.PREPARING) cancel()
        // Salto a mano (siguiente, anterior, elegir en la cola) con la música sonando. El salto
        // propio de la mezcla ocurre ya en FADING y no entra aquí.
        if (phase == Phase.IDLE && reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) startManualFade()
    }

    private fun schedule() {
        handler.removeCallbacks(tick)
        if (phase != Phase.IDLE || (fadeMs > 0 && player.isPlaying)) handler.post(tick)
    }

    // ---- Máquina de estados ----

    private fun step() {
        if (phase == Phase.IDLE && player.isPlaying) {
            lastItem = player.currentMediaItem
            lastPositionMs = player.currentPosition
            lastSeenAt = SystemClock.elapsedRealtime()
        }
        when (phase) {
            Phase.IDLE -> maybePrepare()
            Phase.PREPARING -> maybeStart()
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
        tail = try {
            buildTail().apply {
                setMediaItem(item, tailStartMs)
                playWhenReady = false
                prepare()
            }
        } catch (e: Exception) {
            EventLog.log("Crossfade: no se pudo preparar la mezcla (${e.javaClass.simpleName})")
            null
        }
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

    /**
     * Mezcla corta al saltar a mano: la canción nueva ya está en el principal; se baja a 0 y sube,
     * y un auxiliar retoma la anterior donde iba (estimado) y la apaga. Mientras el auxiliar se
     * prepara (unas décimas) suena solo la nueva subiendo: nunca hay silencio.
     */
    private fun startManualFade() {
        val item = lastItem ?: return
        if (fadeMs <= 0 || !onSkip || !player.playWhenReady) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastSeenAt > CrossfadePlan.MAX_STALE_MS) return
        val position = CrossfadePlan.estimatePosition(lastPositionMs, now - lastSeenAt)
        lastItem = null
        tail = try {
            buildTail().apply {
                setMediaItem(item, position)
                playWhenReady = true
                prepare()
            }
        } catch (e: Exception) {
            EventLog.log("Crossfade: no se pudo mezclar el salto (${e.javaClass.simpleName})")
            null
        }
        val aux = tail ?: return
        targetVolume = player.volume
        aux.volume = targetVolume
        activeFadeMs = CrossfadePlan.manualFadeMs(fadeMs)
        phase = Phase.FADING
        fadeStartedAt = now
        setMainVolume(0f)
        schedule()
    }

    private fun finish(restore: Boolean) {
        releaseTail()
        if (restore) player.volume = targetVolume
        lastSetVolume = -1f
        phase = Phase.IDLE
    }

    /** Corta la mezcla en curso (o la preparación) y deja el volumen como estaba. */
    private fun cancel() {
        val wasFading = phase == Phase.FADING
        releaseTail()
        if (wasFading && lastSetVolume >= 0f && player.volume == lastSetVolume) player.volume = targetVolume
        lastSetVolume = -1f
        phase = Phase.IDLE
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
        // Cada 250 ms: la posición anotada para retomar la canción anterior al saltar no se aleja más.
        const val IDLE_TICK_MS = 250L
        const val ACTIVE_TICK_MS = 40L
        const val ALIGN_TOLERANCE_MS = 20L
    }
}
