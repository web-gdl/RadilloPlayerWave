package com.radillo.playerwave.core.model

import com.radillo.playerwave.core.ui.CoverOverrides

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
     * Carátula: la cambiada en el editor si la hay ([CoverOverrides]); si no, la del álbum en
     * MediaStore (content://media/external/audio/albumart/<albumId>).
     */
    val albumArtUri: String get() = CoverOverrides.uriFor(id) ?: "content://media/external/audio/albumart/$albumId"
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
