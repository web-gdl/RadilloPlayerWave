package com.radillo.playerwave.core.diagnostics

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro de diagnóstico persistente y diminuto (≤ [MAX_LINES] líneas en un archivo de texto).
 *
 * En el autoestéreo no hay `adb`: lo que pasa (arranques, pausas y su causa, cierres inesperados,
 * cambios del modo de dibujo) se apunta aquí y la pantalla Diagnóstico lo muestra y lo copia en el
 * informe. Escritura síncrona y protegida con un cerrojo: son pocas líneas y muy de vez en cuando.
 */
object EventLog {

    const val MAX_LINES = 60
    private const val FILE = "events.log"
    private const val CRASH_FILE = "last_crash.txt"
    private val lock = Any()
    @Volatile private var dir: File? = null

    /** Debe llamarse una vez al arrancar el proceso (en `Application.onCreate`). */
    fun init(context: Context) {
        dir = context.applicationContext.filesDir
    }

    fun log(message: String) {
        val d = dir ?: return
        val line = "${timestamp()} $message"
        synchronized(lock) {
            try {
                val file = File(d, FILE)
                val lines = if (file.exists()) file.readLines() else emptyList()
                val kept = (lines + line).takeLast(MAX_LINES)
                file.writeText(kept.joinToString("\n", postfix = "\n"))
            } catch (_: Exception) {
                // El diagnóstico nunca debe romper la app.
            }
        }
    }

    fun entries(): List<String> {
        val d = dir ?: return emptyList()
        synchronized(lock) {
            return try {
                File(d, FILE).takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() } ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    // ---- Cierres inesperados (excepciones de Kotlin/Java) ----

    /**
     * Instala un manejador que guarda la traza del cierre antes de dejar que el sistema cierre la
     * app. La próxima vez se puede ver y copiar desde Diagnóstico.
     */
    fun installCrashRecorder() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            recordCrash(thread.name, error)
            previous?.uncaughtException(thread, error)
        }
    }

    fun recordCrash(threadName: String, error: Throwable) {
        val d = dir ?: return
        try {
            val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
            File(d, CRASH_FILE).writeText("${timestamp()} · hilo «$threadName»\n${trace.take(12_000)}")
            log("CIERRE INESPERADO en «$threadName»: ${error.javaClass.simpleName}: ${error.message?.take(160)}")
        } catch (_: Exception) {
        }
    }

    fun lastCrash(): String? {
        val d = dir ?: return null
        return try {
            File(d, CRASH_FILE).takeIf { it.exists() }?.readText()
        } catch (_: Exception) {
            null
        }
    }

    /** Instante (ms) del último cierre registrado, o 0. */
    fun lastCrashMillis(): Long {
        val d = dir ?: return 0L
        return File(d, CRASH_FILE).takeIf { it.exists() }?.lastModified() ?: 0L
    }

    fun clear() {
        val d = dir ?: return
        synchronized(lock) {
            File(d, FILE).delete()
            File(d, CRASH_FILE).delete()
        }
    }

    private fun timestamp(): String = SimpleDateFormat("dd/MM HH:mm:ss", Locale.ROOT).format(Date())
}
