package com.radillo.playerwave.data.library.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Pista marcada como favorita. La clave es el id de MediaStore. */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val trackId: Long,
    val addedAtMs: Long,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAtMs: Long,
)

/** Pertenencia de una pista a una lista, con su posición (0-based). */
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    indices = [Index("playlistId"), Index("trackId")],
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val trackId: Long,
    val position: Int,
)

/** Fila agregada: lista con su número de pistas. */
data class PlaylistRow(
    val id: Long,
    val name: String,
    val createdAtMs: Long,
    val trackCount: Int,
)
