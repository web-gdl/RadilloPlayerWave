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
    const val KEY_CROSSFADE_ON_SKIP = "crossfade_on_skip"
    private const val KEY_CROSSFADE_LAST = "crossfade_last_seconds"
    private const val DEFAULT_CROSSFADE_SECONDS = 6

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
        val value = seconds.coerceIn(0, 12)
        prefs(context).edit().apply {
            putInt(KEY_CROSSFADE_SECONDS, value)
            // Se recuerda la última duración elegida para el botón de activar/desactivar.
            if (value > 0) putInt(KEY_CROSSFADE_LAST, value)
        }.apply()
    }

    /**
     * Botón de «Reproduciendo»: apaga el crossfade o lo vuelve a encender con la última duración
     * elegida (6 s si nunca se eligió). Devuelve los segundos que quedan puestos (0 = apagado).
     */
    fun toggleCrossfade(context: Context): Int {
        val next = if (crossfadeSeconds(context) > 0) 0
        else prefs(context).getInt(KEY_CROSSFADE_LAST, DEFAULT_CROSSFADE_SECONDS).coerceIn(2, 12)
        setCrossfadeSeconds(context, next)
        return next
    }

    /** Mezclar también al cambiar de canción a mano (siguiente, anterior, cola). Activo por defecto. */
    fun crossfadeOnSkip(context: Context): Boolean = prefs(context).getBoolean(KEY_CROSSFADE_ON_SKIP, true)

    fun setCrossfadeOnSkip(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_CROSSFADE_ON_SKIP, value).apply()
    }
}
