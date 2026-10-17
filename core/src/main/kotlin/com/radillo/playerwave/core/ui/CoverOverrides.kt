package com.radillo.playerwave.core.ui

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Portadas cambiadas desde el editor, por canción.
 *
 * La portada nueva se escribe dentro del MP3, pero la miniatura que Android guarda por álbum puede
 * tardar en renovarse (en Android 8–9 a veces nunca). Para que el cambio se vea al momento en la
 * app (listas, «Reproduciendo», notificación) se guarda también una copia pequeña aquí y
 * `Track.albumArtUri` la prefiere. Quitar la portada deja una marca `.none` que muestra el marcador.
 *
 * El índice vive en memoria (sin E/S al pedir una URI); se carga una vez al arrancar.
 */
object CoverOverrides {

    private val uris = ConcurrentHashMap<Long, String>()
    @Volatile private var dir: File? = null

    fun init(context: Context) {
        val d = File(context.applicationContext.filesDir, "covers").apply { mkdirs() }
        dir = d
        uris.clear()
        d.listFiles()?.forEach { f ->
            val id = f.nameWithoutExtension.toLongOrNull() ?: return@forEach
            when (f.extension) {
                "jpg" -> uris[id] = uriOf(f)
                "none" -> if (!uris.containsKey(id)) uris[id] = noneUri(f)
            }
        }
    }

    /** URI de la portada propia de [trackId], o null si nunca se cambió. */
    fun uriFor(trackId: Long): String? = uris[trackId]

    fun set(trackId: Long, jpeg: ByteArray) {
        val d = dir ?: return
        File(d, "$trackId.none").delete()
        val file = File(d, "$trackId.jpg")
        file.writeBytes(jpeg)
        uris[trackId] = uriOf(file)
    }

    fun setNone(trackId: Long) {
        val d = dir ?: return
        File(d, "$trackId.jpg").delete()
        val marker = File(d, "$trackId.none").apply { writeText("") }
        uris[trackId] = noneUri(marker)
    }

    // La fecha en la URI cambia la clave de la caché de carátulas al reemplazar la imagen.
    private fun uriOf(file: File) = "file://${file.absolutePath}?v=${file.lastModified()}"
    private fun noneUri(file: File) = "file://${file.absolutePath}"
}
