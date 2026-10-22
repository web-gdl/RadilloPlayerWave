package com.radillo.playerwave.core.ui

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri

/**
 * Portada **de cada canción**: la que va dentro del archivo (ID3 `APIC` en MP3, `covr` en M4A,
 * imagen de FLAC), la misma que muestra el editor de etiquetas.
 *
 * Antes la app usaba la miniatura del **álbum** de MediaStore (`albumart/<albumId>`): Android agrupa
 * por nombre de álbum, así que canciones distintas con el mismo álbum (o sin álbum) compartían una
 * sola portada, y esa miniatura no siempre se renueva al cambiar el archivo. Si la canción no trae
 * portada, se usa la del álbum como antes.
 *
 * URI propia: `radillo-cover://track/<id>?file=<uri del audio>&album=<albumId>&v=<versión>`; la
 * leen [ArtworkLoader], el extractor de color y el puente del estéreo con [readBytes].
 */
object TrackCover {
    const val SCHEME = "radillo-cover"

    /** Tope de lectura: una portada mayor no se muestra (protege la RAM del equipo). */
    private const val MAX_BYTES = 8 * 1024 * 1024

    fun uri(trackId: Long, fileUri: String, albumId: Long, version: Long): String =
        Uri.Builder()
            .scheme(SCHEME)
            .authority("track")
            .appendPath(trackId.toString())
            .appendQueryParameter("file", fileUri)
            .appendQueryParameter("album", albumId.toString())
            .appendQueryParameter("v", version.toString())
            .build()
            .toString()

    fun albumArtUri(albumId: Long): String = "content://media/external/audio/albumart/$albumId"

    /** Bytes de la imagen de [uri] (portada propia, `content://` o `file://`), o null si no hay. */
    fun readBytes(context: Context, uri: Uri): ByteArray? {
        if (uri.scheme == SCHEME) {
            uri.getQueryParameter("file")?.let { file ->
                embedded(context, Uri.parse(file))?.let { return it }
            }
            val album = uri.getQueryParameter("album")?.toLongOrNull() ?: return null
            return stream(context, Uri.parse(albumArtUri(album)))
        }
        return stream(context, uri)
    }

    private fun embedded(context: Context, file: Uri): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (file.scheme == "file") retriever.setDataSource(file.path) else retriever.setDataSource(context, file)
            retriever.embeddedPicture?.takeIf { it.isNotEmpty() && it.size <= MAX_BYTES }
        } catch (_: Throwable) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {
            }
        }
    }

    private fun stream(context: Context, uri: Uri): ByteArray? = try {
        val input = if (uri.scheme == "file") uri.path?.let { java.io.File(it).inputStream() } else context.contentResolver.openInputStream(uri)
        input?.use { s ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_BYTES) return null
                out.write(buf, 0, n)
            }
            out.toByteArray().takeIf { it.isNotEmpty() }
        }
    } catch (_: Throwable) {
        null
    }
}
