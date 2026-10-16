package com.radillo.playerwave.data.library.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    // ---- Favoritos ----

    @Query("SELECT trackId FROM favorites")
    fun observeFavoriteIds(): Flow<List<Long>>

    @Query(
        """SELECT t.* FROM tracks t INNER JOIN favorites f ON f.trackId = t.id
           ORDER BY f.addedAtMs DESC""",
    )
    fun observeFavoriteTracks(): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM favorites")
    fun observeFavoriteCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE trackId = :trackId")
    suspend fun deleteFavorite(trackId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE trackId = :trackId)")
    suspend fun isFavorite(trackId: Long): Boolean

    // ---- Listas ----

    @Query(
        """SELECT p.id AS id, p.name AS name, p.createdAtMs AS createdAtMs,
                  (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlistId = p.id) AS trackCount
           FROM playlists p ORDER BY p.name COLLATE NOCASE ASC""",
    )
    fun observePlaylists(): Flow<List<PlaylistRow>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observePlaylist(id: Long): Flow<PlaylistEntity?>

    @Query(
        """SELECT t.* FROM tracks t INNER JOIN playlist_tracks pt ON pt.trackId = t.id
           WHERE pt.playlistId = :playlistId ORDER BY pt.position ASC""",
    )
    fun observePlaylistTracks(playlistId: Long): Flow<List<TrackEntity>>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun deletePlaylistTracks(playlistId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun playlistTrackIds(playlistId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistTracks(entries: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun deletePlaylistTrack(playlistId: Long, trackId: Long)

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun updatePosition(playlistId: Long, trackId: Long, position: Int)
}
