package com.radillo.playerwave.core.diagnostics

import android.content.Context
import android.content.SharedPreferences
import com.radillo.playerwave.core.device.RealApiLevel
import java.io.File

/**
 * Cómo dibuja el visualizador: con la GPU (`lockHardwareCanvas`) o con la CPU (`lockCanvas`).
 *
 * Con la GPU, cada frame lo termina de forma asíncrona el hilo de render del sistema. En Android
 * 8–9 (lo que suelen llevar de verdad los autoestéreos MTK aunque declaren 11), destruir la
 * superficie mientras ese hilo aún dibuja puede cerrar la app con un fallo nativo que ningún
 * `try/catch` atrapa. Por eso:
 *
 * - En automático, la GPU solo se usa si el Android real es ≥ 10 y el equipo no es de gama baja.
 * - Mientras una superficie con GPU se crea o se destruye se deja una marca en disco; si el proceso
 *   muere en ese intervalo sin una excepción registrada, en el siguiente arranque se pasa a CPU.
 * - El usuario puede forzarlo desde Diagnóstico.
 */
object RenderMode {

    enum class Choice { AUTO, GPU, CPU }

    private const val PREFS = "render_mode"
    private const val KEY_CHOICE = "choice"
    private const val KEY_FORCED_CPU = "forced_cpu_after_crash"
    private const val MARKER = "gpu_transition.marker"

    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var dir: File? = null
    @Volatile private var lowEnd = false

    fun init(context: Context, lowEndDevice: Boolean) {
        val app = context.applicationContext
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        dir = app.filesDir
        lowEnd = lowEndDevice
        checkPreviousRun()
    }

    var choice: Choice
        get() = prefs?.getString(KEY_CHOICE, null)?.let { name -> Choice.entries.firstOrNull { it.name == name } } ?: Choice.AUTO
        set(value) {
            prefs?.edit()?.putString(KEY_CHOICE, value.name)?.putBoolean(KEY_FORCED_CPU, false)?.apply()
            EventLog.log("Modo de dibujo elegido: $value")
        }

    /** `true` si se pasó a CPU automáticamente tras un cierre durante un cambio de superficie. */
    val forcedCpuAfterCrash: Boolean get() = prefs?.getBoolean(KEY_FORCED_CPU, false) == true

    /** Decisión efectiva para la próxima superficie. */
    fun useGpu(): Boolean = when (choice) {
        Choice.GPU -> true
        Choice.CPU -> false
        Choice.AUTO -> !forcedCpuAfterCrash && !lowEnd && RealApiLevel.effective >= 29
    }

    /** Llamar justo antes de crear o destruir una superficie dibujada con GPU. */
    fun beginGpuTransition() {
        val d = dir ?: return
        try {
            File(d, MARKER).writeText(System.currentTimeMillis().toString())
        } catch (_: Exception) {
        }
    }

    /** Llamar cuando el cambio terminó sin problemas. */
    fun endGpuTransition() {
        dir?.let { File(it, MARKER).delete() }
    }

    private fun checkPreviousRun() {
        val d = dir ?: return
        val marker = File(d, MARKER)
        if (!marker.exists()) return
        val markedAt = marker.lastModified()
        marker.delete()
        // Si hubo excepción registrada después de la marca, el culpable no fue la GPU.
        if (EventLog.lastCrashMillis() >= markedAt) return
        if (choice != Choice.AUTO) return
        prefs?.edit()?.putBoolean(KEY_FORCED_CPU, true)?.apply()
        EventLog.log("La app se cerró mientras el visualizador cambiaba de superficie con GPU: se usará la CPU")
    }
}
