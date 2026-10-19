package com.radillo.playerwave.player.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.radillo.playerwave.core.diagnostics.EventLog
import com.radillo.playerwave.player.R

/**
 * Mantiene viva la app mientras la música espera a que se vuelva a encender el auto.
 *
 * Al apagar el auto el estéreo pausa la música (y la reanuda al encender). En pausa, el servicio
 * de reproducción deja el primer plano a los 10 minutos, y Android 8 detiene los servicios de las
 * apps en segundo plano: al encender ya no había nadie para seguir y el estéreo se olvidaba de la
 * fuente (sin canción en el widget). Este servicio en primer plano, sin trabajo propio, lo evita.
 *
 * Solo se usa en estéreos Jancar ([com.radillo.playerwave.player.jancar.JancarBridge]).
 */
class CarStandbyService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Siempre se entra en primer plano primero: Android 8 cierra la app si un servicio pedido
        // con startForegroundService no lo hace, aunque ya no haga falta.
        try {
            val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
        } catch (e: Exception) {
            EventLog.log("Espera del encendido: no se pudo quedar en primer plano (${e.javaClass.simpleName})")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!wanted) stopSelfNow()
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun stopSelfNow() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(): android.app.Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && manager?.getNotificationChannel(CHANNEL_ID) == null) {
            manager?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.standby_channel), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
            )
        }
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            android.app.PendingIntent.getActivity(this, 0, it, android.app.PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(androidx.media3.session.R.drawable.media3_notification_small_icon)
            .setContentTitle(getString(R.string.standby_title))
            .setContentText(getString(R.string.standby_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "car_standby"
        private const val NOTIFICATION_ID = 0x5244 // distinto del de la reproducción (1001)

        @Volatile private var wanted = false
        @Volatile private var instance: CarStandbyService? = null

        /** Desde el hilo principal. */
        fun hold(context: Context, on: Boolean) {
            wanted = on
            if (on) {
                try {
                    ContextCompat.startForegroundService(context, Intent(context, CarStandbyService::class.java))
                } catch (e: Exception) {
                    EventLog.log("Espera del encendido: no se pudo iniciar (${e.javaClass.simpleName})")
                }
            } else {
                // Si aún no arrancó, se detiene solo al llegar (tras entrar en primer plano).
                instance?.stopSelfNow()
            }
        }
    }
}
