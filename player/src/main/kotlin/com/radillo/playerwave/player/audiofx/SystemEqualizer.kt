package com.radillo.playerwave.player.audiofx

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import com.radillo.playerwave.core.diagnostics.EventLog

/**
 * Ecualizador **del sistema** (la app ya no trae uno propio).
 *
 * En el estéreo Jancar es la app de audio del equipo (`com.jancar.audiosettings`): ecualizador,
 * balance izquierda/derecha y adelante/atrás (fader) sobre todo el audio, el mismo que abren el
 * escritorio y la música de fábrica. En un teléfono es el panel estándar de Android
 * ([AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL]), si el fabricante trae uno.
 *
 * Además se anuncia la sesión de audio del reproductor a los ecualizadores del sistema que actúan
 * por sesión ([onSessionId]), como hace cualquier reproductor de Android.
 */
object SystemEqualizer {

    /** Destinos por orden de preferencia (los mismos que usan las apps de fábrica del estéreo). */
    private val CAR_TARGETS = listOf(
        ComponentName("com.jancar.audiosettings", "com.jancar.audiosettings.ui.activity.MainActivity"),
        ComponentName("com.jancar.settings", "com.jancar.settings.activity.AudioSettingActivity"),
    )
    private const val CAR_PACKAGE = "com.jancar.audiosettings"

    @Volatile private var sessionId = 0

    /**
     * Abre el ecualizador del sistema. Devuelve `false` si el equipo no tiene ninguno (la UI
     * lo dice). [context] puede ser una actividad o cualquier contexto.
     */
    fun open(context: Context): Boolean {
        val intents = buildList {
            CAR_TARGETS.forEach { add(Intent().setComponent(it)) }
            context.packageManager.getLaunchIntentForPackage(CAR_PACKAGE)?.let(::add)
            add(
                Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                    .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                    .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                    .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC),
            )
        }
        for (intent in intents) {
            if (intent.resolveActivity(context.packageManager) == null) continue
            try {
                if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        EventLog.log("Ecualizador: el equipo no tiene ecualizador del sistema")
        return false
    }

    /** `true` si hay algún ecualizador del sistema que abrir. */
    fun isAvailable(context: Context): Boolean {
        val pm = context.packageManager
        return CAR_TARGETS.any { Intent().setComponent(it).resolveActivity(pm) != null } ||
            pm.getLaunchIntentForPackage(CAR_PACKAGE) != null ||
            Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).resolveActivity(pm) != null
    }

    /**
     * El servicio de reproducción avisa del `audioSessionId` del reproductor (0 al cerrarse): se
     * cierra la sesión anterior y se abre la nueva ante los ecualizadores del sistema.
     */
    fun onSessionId(context: Context, id: Int) {
        val previous = sessionId
        if (previous == id) return
        sessionId = id
        if (previous > 0) broadcast(context, AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION, previous)
        if (id > 0) broadcast(context, AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, id)
    }

    private fun broadcast(context: Context, action: String, session: Int) {
        try {
            context.sendBroadcast(
                Intent(action)
                    .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, session)
                    .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                    .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC),
            )
        } catch (_: Exception) {
            // Sin ecualizador del sistema que escuche, no pasa nada.
        }
    }
}
