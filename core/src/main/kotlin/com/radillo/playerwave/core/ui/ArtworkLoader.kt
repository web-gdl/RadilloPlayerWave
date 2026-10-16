package com.radillo.playerwave.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.LruCache
import android.widget.ImageView
import java.util.WeakHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Cargador de carátulas mínimo, pensado para 1 GB de RAM (sustituye a Coil):
 * - decodifica en 2 hilos de baja prioridad con `inSampleSize` al tamaño pedido y en `RGB_565`
 *   (la mitad de memoria que ARGB_8888; las carátulas no tienen transparencia);
 * - caché LRU por bytes (≤ 1/16 del heap, máx. 6 MB) y caché de «sin carátula» para no repetir
 *   lecturas fallidas;
 * - cancela la carga anterior cuando una fila se recicla con otra canción.
 *
 * Se usa solo desde el hilo principal.
 */
class ArtworkLoader private constructor(context: Context) {

    private val resolver = context.applicationContext.contentResolver
    private val main = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newFixedThreadPool(2) { r ->
        Thread(Runnable {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            r.run()
        }, "RadilloArtwork").apply { isDaemon = true }
    }
    private val cacheBytes = (Runtime.getRuntime().maxMemory() / 16).coerceAtMost(6L * 1024 * 1024).toInt()
    private val cache = object : LruCache<String, Bitmap>(cacheBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val misses = LruCache<String, Boolean>(256)
    private val pending = WeakHashMap<ImageView, Future<*>>()
    private val requested = WeakHashMap<ImageView, String>()

    /**
     * Muestra en [view] la carátula de [uri] decodificada a unos [sizePx] de lado, o el recurso
     * [placeholder] si no hay carátula. [uri] null muestra directamente el marcador.
     */
    fun load(view: ImageView, uri: String?, sizePx: Int, placeholder: Int) {
        pending.remove(view)?.cancel(true)
        if (uri == null) {
            requested.remove(view)
            view.setImageResource(placeholder)
            return
        }
        val key = "$uri@$sizePx"
        requested[view] = key
        cache.get(key)?.let {
            view.setImageBitmap(it)
            return
        }
        view.setImageResource(placeholder)
        if (misses.get(key) != null) return
        pending[view] = executor.submit(Runnable {
            val bitmap = decode(Uri.parse(uri), sizePx)
            main.post {
                if (bitmap == null) misses.put(key, true) else cache.put(key, bitmap)
                if (requested[view] == key) {
                    pending.remove(view)
                    if (bitmap != null) view.setImageBitmap(bitmap)
                }
            }
        })
    }

    /** Vacía la caché (llamar desde `onTrimMemory`). */
    fun trim() {
        cache.evictAll()
    }

    private fun decode(uri: Uri, sizePx: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || Thread.currentThread().isInterrupted) {
            null
        } else {
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }
    } catch (_: Throwable) {
        null
    }

    companion object {
        @Volatile private var instance: ArtworkLoader? = null

        fun get(context: Context): ArtworkLoader =
            instance ?: synchronized(this) { instance ?: ArtworkLoader(context).also { instance = it } }
    }
}
