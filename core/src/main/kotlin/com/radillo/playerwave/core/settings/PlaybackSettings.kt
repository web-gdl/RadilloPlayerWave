package com.radillo.playerwave.core.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Ajustes que lee el servicio de reproducción (módulo `:player`, sin inyección) y escribe la
 * pantalla de Ajustes. Viven en `:core` para que ambos lados compartan archivo y claves.
 */
object PlaybackSettings {

    const val FILE = "playback_settings"
    const val KEY_KEEP_PLAYING = "keep_playing_on_focus_loss"
    const val KEY_CROSSFADE_SECONDS = "crossfade_seconds"

    /** Duraciones que ofrece Ajustes (0 = apagado). Pocas opciones: se elige en un vistazo. */
    val CROSSFADE_OPTIONS = intArrayOf(0, 2, 4, 6, 8, 10, 12)

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * Seguir sonando cuando otra app se queda el audio de forma permanente.
     *
     * En los autoestéreos el lanzador, la radio o el Bluetooth del propio equipo suelen pedir el
     * foco de audio al volver al escritorio, y un reproductor «educado» se pausa: es el «no suena en
     * segundo plano». Por defecto se mantiene la música en pantallas de coche ([carDefault]) y se
     * cede en el resto. Las interrupciones breves (navegación, llamadas) pausan o bajan el volumen
     * igualmente y la música vuelve sola.
     *
     * Guardado como -1 = automático, 0 = no, 1 = sí.
     */
    fun keepPlayingOnFocusLoss(context: Context, carDefault: Boolean): Boolean =
        when (prefs(context).getInt(KEY_KEEP_PLAYING, -1)) {
            0 -> false
            1 -> true
            else -> carDefault
        }

    fun setKeepPlayingOnFocusLoss(context: Context, value: Boolean) {
        prefs(context).edit().putInt(KEY_KEEP_PLAYING, if (value) 1 else 0).apply()
    }

    /** Segundos de mezcla entre canciones (crossfade); 0 = apagado (por defecto). */
    fun crossfadeSeconds(context: Context): Int = prefs(context).getInt(KEY_CROSSFADE_SECONDS, 0).coerceIn(0, 12)

    fun setCrossfadeSeconds(context: Context, seconds: Int) {
        prefs(context).edit().putInt(KEY_CROSSFADE_SECONDS, seconds.coerceIn(0, 12)).apply()
    }
}
