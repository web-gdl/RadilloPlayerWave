package com.radillo.playerwave.player.service

import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.radillo.playerwave.core.device.DeviceProfile
import com.radillo.playerwave.core.diagnostics.EventLog
import com.radillo.playerwave.core.model.Track
import com.radillo.playerwave.core.settings.PlaybackSettings
import com.radillo.playerwave.player.audiofx.AudioEffectsHub
import com.radillo.playerwave.player.audiotap.AudioTapHub
import com.radillo.playerwave.player.audiotap.RadilloRenderersFactory
import com.radillo.playerwave.player.crossfade.CrossfadeController
import com.radillo.playerwave.player.engine.MediaItemMapper
import com.radillo.playerwave.player.jancar.JancarBridge
import java.io.File
import java.util.concurrent.Executors

/**
 * Servicio de reproducción. Media3 se encarga de la notificación, los controles de la pantalla
 * bloqueada, los botones de auriculares y Bluetooth, y del servicio en primer plano.
 *
 * Además:
 * - **Recuerda** la cola, la canción, la posición, el aleatorio y la repetición, y los restaura al
 *   crearse (la app vuelve a donde estaba aunque el equipo se haya apagado con la llave).
 * - **Foco de audio de coche** ([KeepPlayingAudioFocus]): si el ajuste está activo, la música no se
 *   pausa cuando el lanzador o la radio del equipo se quedan el audio.
 * - **Crossfade** ([CrossfadeController]): mezcla el final de una canción con el principio de la
 *   siguiente si el ajuste está activo.
 * - **Estéreos Jancar** ([JancarBridge]): se registra como fuente del sistema (volante, llamadas,
 *   apagar y encender el auto, app de memoria). Mientras está conectado, el foco lo pide el
 *   servicio del estéreo y la app no lo pide.
 * - Apunta en [EventLog] por qué se pausa o se detiene, para diagnosticar sin `adb`.
 *
 * El `ExoPlayer` se crea aquí (único proceso) y la UI se conecta mediante `MediaController`.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private var session: MediaLibrarySession? = null
    private var exoPlayer: ExoPlayer? = null
    private var focus: KeepPlayingAudioFocus? = null
    private var bridge: JancarBridge? = null
    private var crossfade: CrossfadeController? = null
    /** El estéreo pidió reanudar al arrancar, pero la cola aún no se había restaurado. */
    private var playAfterRestore = false
    private lateinit var store: PlaybackStateStore
    private lateinit var settings: SharedPreferences
    private var carDefault = false
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    private val handler = Handler(Looper.getMainLooper())
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "RadilloStateWriter").apply { priority = Thread.MIN_PRIORITY } }

    /** Pistas de la cola ya convertidas: solo se recalculan cuando cambia la cola. */
    private var cachedTracks: List<Track>? = null
    /** Para cada elemento del reproductor, su índice en [cachedTracks] (o -1 si no se pudo convertir). */
    private var cachedIndexMap: IntArray = IntArray(0)
    private val saveRunnable = Runnable { saveState() }
    private val periodicSave = object : Runnable {
        override fun run() {
            saveState()
            handler.postDelayed(this, PERIODIC_SAVE_MS)
        }
    }

    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PlaybackSettings.KEY_KEEP_PLAYING) applyFocusMode()
    }

    override fun onCreate() {
        super.onCreate()
        EventLog.init(this)
        store = PlaybackStateStore(File(filesDir, STATE_FILE))
        settings = PlaybackSettings.prefs(this)
        carDefault = DeviceProfile.detect(this).fixedLandscape
        val player = buildPlayer()
        exoPlayer = player
        focus = KeepPlayingAudioFocus(this, player)
        bridge = JancarBridge.create(this, player, onActiveChanged = ::applyFocusMode)
        crossfade = CrossfadeController(this, player, audioAttributes)
        applyFocusMode()
        settings.registerOnSharedPreferenceChangeListener(settingsListener)
        restoreState(player)
        val builder = MediaLibrarySession.Builder(this, player, LibraryCallback())
        sessionActivityIntent()?.let(builder::setSessionActivity)
        session = builder.build()
        EventLog.log("Servicio de reproducción creado")
        BootResume.attach(::resumeAfterBoot)
    }

    private fun buildPlayer(): ExoPlayer {
        // El tap de PCM alimenta al visualizador sin permisos de micrófono. El offload de audio
        // queda deshabilitado (valor por defecto): con offload el PCM no pasa por los AudioProcessor.
        val renderersFactory = RadilloRenderersFactory(this, AudioTapHub.newProcessor())
        // Archivos locales: basta con unos segundos de búfer. El valor por defecto de Media3 reserva
        // ~13 MB para audio; aquí se limita a 2 MB para dispositivos con 1 GB de RAM.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 10_000,
                /* maxBufferMs = */ 30_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_000,
            )
            .setTargetBufferBytes(2 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(false)
            .setBackBuffer(/* backBufferDurationMs = */ 0, /* retainBackBufferFromKeyframe = */ false)
            .build()
        val player = ExoPlayer.Builder(this, renderersFactory)
            .setLoadControl(loadControl)
            // El foco de audio se configura en applyFocusMode() según el ajuste.
            .setAudioAttributes(audioAttributes, false)
            // Pausa al desconectar auriculares (salvo en modo coche: ver applyFocusMode()).
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        // El ecualizador de la plataforma se liga al id de sesión de audio del reproductor.
        AudioEffectsHub.onSessionId(player.audioSessionId)
        player.addListener(PlayerEvents())
        return player
    }

    /**
     * Quién pide el foco de audio:
     * - estéreo Jancar conectado: nadie en la app (lo pide su servicio en nombre de la app);
     * - «Seguir sonando»: el foco propio de coche;
     * - si no, ExoPlayer, que cede el audio como cualquier reproductor.
     */
    private fun applyFocusMode() {
        val exo = exoPlayer ?: return
        val external = bridge?.active == true
        val keepPlaying = !external && PlaybackSettings.keepPlayingOnFocusLoss(this, carDefault)
        val car = external || keepPlaying
        exo.setAudioAttributes(audioAttributes, !car)
        // Los autoestéreos MTK emiten «audio ruidoso» al cambiar de fuente o de ruta (radio, BT,
        // escritorio); en modo coche no debe pausar. En el móvil sí: se desenchufan auriculares.
        exo.setHandleAudioBecomingNoisy(!car)
        focus?.enabled = keepPlaying
    }

    /**
     * El estéreo abrió la app al arrancar (app de memoria): si el usuario la tenía sonando, sigue.
     * Si la cola aún se está restaurando, se reproduce en cuanto llegue.
     */
    private fun resumeAfterBoot() {
        val b = bridge ?: return
        if (!b.userWantsPlayback) {
            EventLog.log("Arranque del estéreo: la música estaba en pausa; no se reanuda")
            return
        }
        // Se espera a que el servicio del estéreo se conecte para que sea él quien pida el foco.
        b.whenConnected(BOOT_CONNECT_WAIT_MS) {
            val player = exoPlayer ?: return@whenConnected
            if (player.mediaItemCount == 0) playAfterRestore = true else startPlayback(player)
        }
    }

    private fun startPlayback(player: ExoPlayer) {
        if (player.playWhenReady && player.playbackState != Player.STATE_IDLE) return
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
        player.play()
        EventLog.log("Arranque del estéreo: la música sigue donde iba")
    }

    private inner class PlayerEvents : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            focus?.onPlayWhenReadyChanged(playWhenReady)
            if (!playWhenReady) {
                val why = when (reason) {
                    Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "el usuario o un mando"
                    Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "otra app pidió el audio"
                    Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "se desconectó la salida de audio"
                    Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> "fin de la canción"
                    Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "un control remoto"
                    else -> "motivo $reason"
                }
                EventLog.log("Pausa: $why")
                scheduleSave()
            }
            if (playWhenReady) {
                handler.removeCallbacks(periodicSave)
                handler.postDelayed(periodicSave, PERIODIC_SAVE_MS)
            } else {
                handler.removeCallbacks(periodicSave)
            }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            AudioEffectsHub.onSessionId(audioSessionId)
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            cachedTracks = null
            scheduleSave()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = scheduleSave()
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = scheduleSave()
        override fun onRepeatModeChanged(repeatMode: Int) = scheduleSave()

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            if (playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) {
                EventLog.log("Reproducción suspendida por el sistema (motivo $playbackSuppressionReason)")
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            EventLog.log("Error de reproducción: ${error.errorCodeName} ${error.message?.take(120).orEmpty()}")
        }
    }

    // ---- Persistencia ----

    /**
     * Restaura la cola guardada **sin bloquear el arranque**: leer el JSON y crear los MediaItem se
     * hace en el hilo de escritura (con 3000 canciones, en el autoestéreo eran ~2 s de pantalla
     * congelada en el hilo principal); en el principal solo se entrega la lista al reproductor.
     * Como ese hilo es el mismo que guarda, la lectura siempre ocurre antes de cualquier guardado.
     */
    private fun restoreState(player: ExoPlayer) {
        val started = System.currentTimeMillis()
        writer.execute {
            val saved = store.read() ?: return@execute
            val items = saved.tracks.map(MediaItemMapper::toMediaItem)
            handler.post {
                // Si el usuario ya puso algo a sonar mientras tanto, lo suyo manda.
                if (session == null || player.mediaItemCount > 0) return@post
                player.shuffleModeEnabled = saved.shuffle
                player.repeatMode = saved.repeatMode
                if (items.isEmpty()) return@post
                // Sin prepare(): no se decodifica nada hasta que el usuario pulse reproducir.
                player.setMediaItems(items, saved.index.coerceIn(0, items.lastIndex), saved.positionMs)
                EventLog.log("Cola restaurada: ${items.size} canciones (${System.currentTimeMillis() - started} ms)")
                if (playAfterRestore) {
                    playAfterRestore = false
                    startPlayback(player)
                }
            }
        }
    }

    private fun scheduleSave() {
        handler.removeCallbacks(saveRunnable)
        handler.postDelayed(saveRunnable, SAVE_DEBOUNCE_MS)
    }

    /** Toma la instantánea en el hilo principal (el reproductor lo exige) y escribe en otro hilo. */
    private fun saveState(sync: Boolean = false) {
        val player = session?.player ?: return
        val tracks = cachedTracks ?: run {
            val list = ArrayList<Track>(player.mediaItemCount)
            val map = IntArray(player.mediaItemCount) { -1 }
            for (i in 0 until player.mediaItemCount) {
                MediaItemMapper.toTrack(player.getMediaItemAt(i))?.let {
                    map[i] = list.size
                    list += it
                }
            }
            cachedIndexMap = map
            cachedTracks = list
            list
        }
        val current = player.currentMediaItemIndex
        val saved = SavedPlayback(
            tracks = tracks,
            index = cachedIndexMap.getOrNull(current)?.takeIf { it >= 0 } ?: 0,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
        )
        // Todas las escrituras pasan por el mismo hilo (nunca dos a la vez sobre el mismo archivo);
        // al cerrarse el servicio se espera a que termine.
        val task = writer.submit { store.write(saved) }
        if (sync) {
            try {
                task.get(SYNC_SAVE_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (_: Exception) {
            }
        }
    }

    private fun sessionActivityIntent(): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    /** Solo para pruebas del módulo: acceso a la sesión sin necesidad de un ControllerInfo. */
    internal fun sessionForTest(): MediaLibrarySession? = session

    /** Si el usuario cierra la app desde "recientes" y no está sonando nada, el servicio se apaga. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        saveState(sync = true)
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            EventLog.log("App cerrada desde recientes sin música: servicio detenido")
            // Que el volante no apunte a una app cerrada ni el estéreo la reabra al arrancar.
            bridge?.closeSource()
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveState(sync = true)
        handler.removeCallbacksAndMessages(null)
        BootResume.detach()
        settings.unregisterOnSharedPreferenceChangeListener(settingsListener)
        // Suelta la fuente del estéreo: si no, el volante apuntaría a una app sin reproductor.
        bridge?.release()
        bridge = null
        crossfade?.release()
        crossfade = null
        focus?.release()
        focus = null
        AudioEffectsHub.onSessionId(0)
        session?.run {
            player.release()
            release()
        }
        session = null
        exoPlayer = null
        writer.shutdown()
        EventLog.log("Servicio de reproducción destruido")
        super.onDestroy()
    }

    private class LibraryCallback : MediaLibrarySession.Callback {
        /**
         * Los MediaItems enviados por un controlador pueden llegar sin URI. Se restauran aquí para
         * que `setMediaItems`/`addMediaItems` funcionen tanto en proceso como desde otros clientes.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.mapTo(ArrayList(mediaItems.size), MediaItemMapper::withPlayableUri)
            return Futures.immediateFuture(resolved)
        }
    }

    private companion object {
        const val STATE_FILE = "playback_state.json"
        const val SAVE_DEBOUNCE_MS = 1_000L
        const val PERIODIC_SAVE_MS = 15_000L
        const val SYNC_SAVE_TIMEOUT_MS = 2_000L
        const val BOOT_CONNECT_WAIT_MS = 3_000L
    }
}
