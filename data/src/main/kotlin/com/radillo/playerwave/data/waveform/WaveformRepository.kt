package com.radillo.playerwave.data.waveform

import android.content.Context
import android.net.Uri
import android.os.Process
import com.radillo.playerwave.core.diagnostics.EventLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Onda de sonido de cada canción para la barra de progreso. Se calcula una vez, con prioridad baja
 * y de una en una, y se guarda en la caché de la app; las siguientes veces sale del archivo al
 * instante. Si no se puede calcular, devuelve `null` y la barra se dibuja lisa.
 */
@Singleton
class WaveformRepository @Inject constructor(@ApplicationContext private val context: Context) {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val worker = Dispatchers.IO.limitedParallelism(1)
    private val dir: File get() = File(context.cacheDir, "waveforms")
    private var loggedFailure = false
    /** Canciones que no se pudieron medir en esta sesión: no se reintenta en cada reproducción. */
    private val failed = HashSet<String>()

    /**
     * Onda de la canción [trackId]. [durationMs] entra en la clave por si el archivo se sustituye
     * por otro. Cancelable: al cambiar de canción se abandona el cálculo en curso.
     */
    suspend fun load(trackId: Long, uri: String, durationMs: Long): ByteArray? = withContext(worker) {
        val file = File(dir, "${trackId}_$durationMs.wf")
        readCached(file)?.let { return@withContext it }
        if (file.name in failed) return@withContext null
        // Margen para no competir con el arranque de la canción ni calcular las que se saltan rápido.
        delay(START_DELAY_MS)
        val job = coroutineContext
        val previousPriority = Process.getThreadPriority(Process.myTid())
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        val peaks = try {
            WaveformExtractor.extract(context, Uri.parse(uri)) { !job.isActive }
        } finally {
            Process.setThreadPriority(previousPriority)
        }
        if (peaks != null) {
            runCatching {
                dir.mkdirs()
                file.writeBytes(peaks)
                trim()
            }
        } else if (job.isActive) {
            failed += file.name
            if (loggedFailure) return@withContext null
            loggedFailure = true
            EventLog.log("Onda de sonido: no se pudo calcular para una canción (se usa la barra normal)")
        }
        peaks
    }

    private fun readCached(file: File): ByteArray? = runCatching {
        if (file.isFile && file.length() == WaveformShape.BUCKETS.toLong()) file.readBytes() else null
    }.getOrNull()

    /** La caché no crece sin fin: se quedan las [MAX_FILES] ondas más recientes. */
    private fun trim() {
        val files = dir.listFiles() ?: return
        if (files.size <= MAX_FILES) return
        files.sortedBy { it.lastModified() }.take(files.size - MAX_FILES).forEach { it.delete() }
    }

    private companion object {
        const val START_DELAY_MS = 1_500L
        const val MAX_FILES = 400
    }
}
