package com.radillo.playerwave.data.library

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.provider.MediaStore
import com.radillo.playerwave.data.library.db.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Lee las pistas musicales de `MediaStore`. No toca rutas de archivo para reproducir:
 * la URI de contenido es lo único que necesita ExoPlayer, de modo que funciona con
 * almacenamiento con ámbito (Android 10+) sin permisos adicionales.
 *
 * La columna `DATA` está obsoleta pero sigue siendo legible en todas las versiones y es
 * la forma más fiable de agrupar por carpeta; `RELATIVE_PATH` (API 29+) es el respaldo.
 */
class MediaStoreScanner @Inject constructor(
    private val contentResolver: ContentResolver,
) {

    suspend fun scan(): List<TrackEntity> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > ?"
        val selectionArgs = arrayOf(MIN_DURATION_MS.toString())

        val result = ArrayList<TrackEntity>()
        val cursor: Cursor? = try {
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            )
        } catch (e: SecurityException) {
            // Sin permiso: biblioteca vacía; la UI muestra la pantalla de permisos.
            null
        }
        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val artistIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val data = c.getStringOrNull(dataCol).orEmpty()
                val fileName = c.getStringOrNull(nameCol) ?: data.substringAfterLast('/')
                result += TrackEntity(
                    id = id,
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString(),
                    title = c.getStringOrNull(titleCol)?.takeIf { it.isNotBlank() } ?: fileName.substringBeforeLast('.'),
                    artist = normalizeUnknown(c.getStringOrNull(artistCol)),
                    artistId = c.getLong(artistIdCol),
                    album = normalizeUnknown(c.getStringOrNull(albumCol)),
                    albumId = c.getLong(albumIdCol),
                    durationMs = c.getLong(durationCol),
                    trackNumber = normalizeTrackNumber(c.getInt(trackCol)),
                    year = c.getInt(yearCol),
                    folderPath = FolderPath.parentOf(data),
                    fileName = fileName,
                    mimeType = c.getStringOrNull(mimeCol).orEmpty(),
                    dateAddedSec = c.getLong(addedCol),
                    sizeBytes = c.getLong(sizeCol),
                )
            }
        }
        result
    }

    private fun Cursor.getStringOrNull(column: Int): String? = if (isNull(column)) null else getString(column)

    companion object {
        /** Se descartan clips de menos de 5 s (tonos, notificaciones mal etiquetadas). */
        const val MIN_DURATION_MS = 5_000L
        const val UNKNOWN = "<unknown>"

        fun normalizeUnknown(value: String?): String =
            if (value.isNullOrBlank() || value == UNKNOWN) "" else value

        /** MediaStore codifica el número de disco en los millares (p. ej. 2003 = disco 2, pista 3). */
        fun normalizeTrackNumber(raw: Int): Int = if (raw > 0) raw % 1000 else 0
    }
}

/** Utilidades de ruta puras (sin Android) para poder probarlas con JUnit. */
object FolderPath {
    fun parentOf(path: String): String {
        val idx = path.lastIndexOf('/')
        return if (idx <= 0) "" else path.substring(0, idx)
    }

    fun displayName(path: String): String = path.substringAfterLast('/', path)
}
