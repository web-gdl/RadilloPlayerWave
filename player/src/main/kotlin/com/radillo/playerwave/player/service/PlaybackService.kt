package com.radillo.playerwave.player.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.radillo.playerwave.player.audiofx.AudioEffectsHub
import com.radillo.playerwave.player.audiotap.AudioTapHub
import com.radillo.playerwave.player.audiotap.RadilloRenderersFactory
import com.radillo.playerwave.player.engine.MediaItemMapper

/**
 * Servicio de reproducción. Media3 se encarga de la notificación, los controles de la pantalla
 * bloqueada, los botones de auriculares y Bluetooth, y del servicio en primer plano.
 *
 * El `ExoPlayer` se crea aquí (único proceso) y la UI se conecta mediante `MediaController`.
 * No se usa inyección de dependencias en el servicio para mantener su ciclo de vida simple y
 * testeable con Robolectric.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private var session: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
        val player = buildPlayer()
        val builder = MediaLibrarySession.Builder(this, player, LibraryCallback())
        sessionActivityIntent()?.let(builder::setSessionActivity)
        session = builder.build()
    }

    private fun buildPlayer(): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        // El tap de PCM alimenta al visualizador sin permisos de micrófono. El offload de audio
        // queda deshabilitado (valor por defecto): con offload el PCM no pasa por los AudioProcessor.
        val renderersFactory = RadilloRenderersFactory(this, AudioTapHub.newProcessor())
        val player = ExoPlayer.Builder(this, renderersFactory)
            // handleAudioFocus = true: pausa en llamadas y cede el foco a otras apps.
            .setAudioAttributes(audioAttributes, true)
            // Pausa al desconectar auriculares.
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        // El ecualizador de la plataforma se liga al id de sesión de audio del reproductor.
        AudioEffectsHub.onSessionId(player.audioSessionId)
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                AudioEffectsHub.onSessionId(audioSessionId)
            }
        })
        return player
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
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        AudioEffectsHub.onSessionId(0)
        session?.run {
            player.release()
            release()
        }
        session = null
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
}
