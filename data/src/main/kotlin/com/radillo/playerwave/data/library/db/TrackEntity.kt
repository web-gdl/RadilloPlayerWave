package com.radillo.playerwave.data.library.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.radillo.playerwave.core.model.Track

/** Caché local de la fila de MediaStore. `id` es `MediaStore.Audio.Media._ID`. */
@Entity(
    tableName = "tracks",
    indices = [
        Index("albumId"),
        Index("artistId"),
        Index("folderPath"),
        Index("title"),
    ],
)
data class TrackEntity(
    @PrimaryKey val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val artistId: Long,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val trackNumber: Int,
    val year: Int,
    val folderPath: String,
    val fileName: String,
    val mimeType: String,
    val dateAddedSec: Long,
    val sizeBytes: Long,
) {
    fun toTrack(): Track = Track(
        id = id,
        uri = uri,
        title = title,
        artist = artist,
        artistId = artistId,
        album = album,
        albumId = albumId,
        durationMs = durationMs,
        trackNumber = trackNumber,
        year = year,
        folderPath = folderPath,
        fileName = fileName,
        mimeType = mimeType,
        dateAddedSec = dateAddedSec,
        sizeBytes = sizeBytes,
    )

    companion object {
        fun fromTrack(t: Track): TrackEntity = TrackEntity(
            id = t.id,
            uri = t.uri,
            title = t.title,
            artist = t.artist,
            artistId = t.artistId,
            album = t.album,
            albumId = t.albumId,
            durationMs = t.durationMs,
            trackNumber = t.trackNumber,
            year = t.year,
            folderPath = t.folderPath,
            fileName = t.fileName,
            mimeType = t.mimeType,
            dateAddedSec = t.dateAddedSec,
            sizeBytes = t.sizeBytes,
        )
    }
}

/** Filas agregadas de las consultas GROUP BY. Los nombres de columna coinciden con los campos. */
data class AlbumRow(
    val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val trackCount: Int,
    val year: Int,
)

data class ArtistRow(
    val id: Long,
    val name: String,
    val albumCount: Int,
    val trackCount: Int,
)

data class FolderRow(
    val path: String,
    val trackCount: Int,
)
