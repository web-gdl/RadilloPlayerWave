package com.radillo.playerwave.player.engine

import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.MediaMetadata
import com.radillo.playerwave.core.model.Track

/**
 * Conversión Track ⇄ MediaItem. Todos los campos del modelo viajan en `MediaMetadata.extras`
 * para que la UI pueda reconstruir el `Track` desde el `MediaController` sin consultar la base
 * de datos. Los campos estándar (título, artista, carátula, duración) se rellenan además para
 * la notificación y la pantalla bloqueada.
 */
@OptIn(UnstableApi::class)
object MediaItemMapper {

    private const val KEY_URI = "radillo.uri"
    private const val KEY_ARTIST_ID = "radillo.artistId"
    private const val KEY_ALBUM_ID = "radillo.albumId"
    private const val KEY_DURATION = "radillo.durationMs"
    private const val KEY_TRACK_NUMBER = "radillo.trackNumber"
    private const val KEY_YEAR = "radillo.year"
    private const val KEY_FOLDER = "radillo.folderPath"
    private const val KEY_FILE_NAME = "radillo.fileName"
    private const val KEY_MIME = "radillo.mimeType"
    private const val KEY_DATE_ADDED = "radillo.dateAddedSec"
    private const val KEY_SIZE = "radillo.sizeBytes"

    fun toMediaItem(track: Track): MediaItem {
        val extras = Bundle().apply {
            putString(KEY_URI, track.uri)
            putLong(KEY_ARTIST_ID, track.artistId)
            putLong(KEY_ALBUM_ID, track.albumId)
            putLong(KEY_DURATION, track.durationMs)
            putInt(KEY_TRACK_NUMBER, track.trackNumber)
            putInt(KEY_YEAR, track.year)
            putString(KEY_FOLDER, track.folderPath)
            putString(KEY_FILE_NAME, track.fileName)
            putString(KEY_MIME, track.mimeType)
            putLong(KEY_DATE_ADDED, track.dateAddedSec)
            putLong(KEY_SIZE, track.sizeBytes)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist.ifBlank { null })
            .setAlbumTitle(track.album.ifBlank { null })
            .setArtworkUri(Uri.parse(track.albumArtUri))
            .setDurationMs(track.durationMs)
            .setTrackNumber(track.trackNumber.takeIf { it > 0 })
            .setRecordingYear(track.year.takeIf { it > 0 })
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(track.uri)
            .setMediaMetadata(metadata)
            .build()
    }

    /** Reconstruye el Track. Devuelve `null` si el item no fue creado por esta app. */
    fun toTrack(item: MediaItem): Track? {
        val id = item.mediaId.toLongOrNull() ?: return null
        val md = item.mediaMetadata
        val extras = md.extras ?: return null
        if (!extras.containsKey(KEY_URI)) return null
        return Track(
            id = id,
            uri = extras.getString(KEY_URI) ?: defaultUri(id).toString(),
            title = md.title?.toString().orEmpty(),
            artist = md.artist?.toString().orEmpty(),
            artistId = extras.getLong(KEY_ARTIST_ID),
            album = md.albumTitle?.toString().orEmpty(),
            albumId = extras.getLong(KEY_ALBUM_ID),
            durationMs = extras.getLong(KEY_DURATION),
            trackNumber = extras.getInt(KEY_TRACK_NUMBER),
            year = extras.getInt(KEY_YEAR),
            folderPath = extras.getString(KEY_FOLDER).orEmpty(),
            fileName = extras.getString(KEY_FILE_NAME).orEmpty(),
            mimeType = extras.getString(KEY_MIME).orEmpty(),
            dateAddedSec = extras.getLong(KEY_DATE_ADDED),
            sizeBytes = extras.getLong(KEY_SIZE),
        )
    }

    /**
     * Un `MediaItem` que llega al servicio desde un controlador puede venir sin `localConfiguration`
     * (la URI). Se restaura desde los extras o, en último caso, desde el id de MediaStore.
     */
    fun withPlayableUri(item: MediaItem): MediaItem {
        if (item.localConfiguration != null) return item
        val fromExtras = item.mediaMetadata.extras?.getString(KEY_URI)
        val uri = fromExtras?.let(Uri::parse)
            ?: item.mediaId.toLongOrNull()?.let(::defaultUri)
            ?: return item
        return item.buildUpon().setUri(uri).build()
    }

    private fun defaultUri(id: Long): Uri =
        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
}
