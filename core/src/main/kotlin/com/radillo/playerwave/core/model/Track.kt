package com.radillo.playerwave.core.model

import com.radillo.playerwave.core.ui.CoverOverrides
import com.radillo.playerwave.core.ui.TrackCover

/**
 * Pista de audio local. `id` es el `MediaStore.Audio.Media._ID`; `uri` es la URI
 * de contenido con la que ExoPlayer abrirá el archivo (no se usan rutas absolutas).
 */
data class Track(
    val id: Long,
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
    /**
     * Carátula del álbum en MediaStore (o la cambiada en el editor, [CoverOverrides]). Es la que
     * se pasa a la sesión multimedia (notificación); para mostrar la de la canción usar [coverUri].
     */
    val albumArtUri: String get() = CoverOverrides.uriFor(id) ?: TrackCover.albumArtUri(albumId)

    /**
     * Portada de esta canción: la cambiada en el editor si la hay; si no, la que trae el propio
     * archivo ([TrackCover]) y, si no trae, la del álbum.
     */
    val coverUri: String get() = CoverOverrides.uriFor(id) ?: TrackCover.uri(id, uri, albumId, sizeBytes)
}

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val trackCount: Int,
    val year: Int,
) {
    val albumArtUri: String get() = "content://media/external/audio/albumart/$id"
}

data class Artist(
    val id: Long,
    val name: String,
    val albumCount: Int,
    val trackCount: Int,
)

data class Folder(
    val path: String,
    val name: String,
    val trackCount: Int,
)

data class Playlist(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val createdAtMs: Long,
)
