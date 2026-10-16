package com.radillo.playerwave.core.device

import android.os.Build

/**
 * Nivel de API **real** del sistema, independiente de lo que declare `Build.VERSION.SDK_INT`.
 *
 * Muchas unidades Android de coche (MTK 8227L y similares) llevan un `build.prop` retocado que
 * anuncia Android 10/11/12 sobre un framework 8.1 o 9. Una app que confíe en `SDK_INT` llama a
 * métodos que no existen y muere con `NoSuchMethodError`. Aquí se comprueba qué clases del
 * framework existen de verdad: cada una se añadió en una versión concreta y no se puede falsear
 * editando propiedades.
 *
 * Por encima de API 34 se confía en `SDK_INT`: los dispositivos modernos no falsean la versión.
 */
object RealApiLevel {

    /** Una clase pública introducida en cada nivel, de más nuevo a más antiguo. */
    private val probes: List<Pair<Int, String>> = listOf(
        34 to "android.credentials.CredentialManager",     // Android 14
        33 to "android.view.OnBackInvokedDispatcher",      // Android 13
        31 to "android.window.SplashScreen",               // Android 12 (12L no añade clases distinguibles)
        30 to "android.media.MediaParser",                 // Android 11
        29 to "android.app.role.RoleManager",              // Android 10
        28 to "android.app.Person",                        // Android 9
        27 to "android.app.WallpaperColors",               // Android 8.1
        26 to "android.app.NotificationChannel",           // Android 8.0
        25 to "android.content.pm.ShortcutManager",        // Android 7.1
    )

    @Volatile private var cached: Int = -1

    /** Nivel de API detectado (24 como suelo). Se calcula una vez. */
    val detected: Int
        get() {
            val c = cached
            if (c > 0) return c
            return detect().also { cached = it }
        }

    /** `true` si el sistema declara una versión mayor que la que realmente tiene. */
    val isSpoofed: Boolean get() = Build.VERSION.SDK_INT > detected

    /** El nivel con el que hay que decidir qué APIs usar: nunca mayor que el real. */
    val effective: Int get() = minOf(Build.VERSION.SDK_INT, detected)

    private fun detect(): Int {
        val declared = Build.VERSION.SDK_INT
        for ((level, className) in probes) {
            if (!exists(className)) continue
            return when {
                level == 34 && declared > 34 -> declared       // moderno y coherente: se acepta lo declarado
                level == 31 && declared == 32 -> 32            // 12 y 12L son indistinguibles por clases
                else -> level
            }
        }
        return 24
    }

    private fun exists(className: String): Boolean = try {
        Class.forName(className, false, RealApiLevel::class.java.classLoader)
        true
    } catch (_: Throwable) {
        false
    }
}
