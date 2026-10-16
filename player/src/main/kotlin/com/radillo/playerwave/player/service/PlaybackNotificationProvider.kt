package com.radillo.playerwave.player.service

import android.app.Notification
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession

/**
 * Proveedor de notificación para mantener el servicio en foreground.
 *
 * Sin esto, Media3 no puede crear una notificación y el servicio se detiene al ir a background.
 * Esta implementación simple cumple con el requisito mínimo: mostrar un canal y mantener
 * el servicio en primer plano mientras hay reproducción.
 */
@OptIn(UnstableApi::class)
class PlaybackNotificationProvider(
    private val context: Context,
) : MediaNotification.Provider {

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "com.radillo.playerwave.playback"
    }

    private var currentNotification: Notification? = null

    init {
        // Crear el canal (obligatorio en Android 8+)
        createNotificationChannel()
    }

    override fun createNotification(
        session: MediaSession,
        customLayout: MediaNotification.MediaNotificationManager.CustomLayout,
    ): MediaNotification {
        // Usar la notificación por defecto de Media3
        val defaultProvider = MediaNotification.DefaultMediaNotificationProvider(context)
        val notification = defaultProvider.createNotification(session, customLayout)
        currentNotification = notification.notification
        return notification
    }

    override fun handleCustomLayout(layout: MediaNotification.MediaNotificationManager.CustomLayout): Boolean {
        // No hay layouts personalizados; usar default
        return false
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "Reproducción de música",
                android.app.NotificationManager.IMPORTANCE_LOW, // bajo para no molestar
            )
            channel.setShowBadge(false)
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
