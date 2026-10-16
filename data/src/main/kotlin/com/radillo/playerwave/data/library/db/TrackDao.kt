package com.radillo.playerwave.data.library.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE albumId = :albumId ORDER BY trackNumber ASC, title COLLATE NOCASE ASC")
    fun observeByAlbum(albumId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE artistId = :artistId ORDER BY album COLLATE NOCASE ASC, trackNumber ASC")
    fun observeByArtist(artistId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE folderPath = :folderPath ORDER BY fileName COLLATE NOCASE ASC")
    fun observeByFolder(folderPath: String): Flow<List<TrackEntity>>

    @Query(
        """SELECT * FROM tracks
           WHERE title LIKE '%' || :query || '%'
              OR artist LIKE '%' || :query || '%'
              OR album LIKE '%' || :query || '%'
           ORDER BY title COLLATE NOCASE ASC
           LIMIT 200""",
    )
    fun search(query: String): Flow<List<TrackEntity>>

    @Query(
        """SELECT albumId AS id, album AS title, MIN(artist) AS artist, MIN(artistId) AS artistId,
                  COUNT(*) AS trackCount, MAX(year) AS year
           FROM tracks GROUP BY albumId ORDER BY title COLLATE NOCASE ASC""",
    )
    fun observeAlbums(): Flow<List<AlbumRow>>

    @Query(
        """SELECT artistId AS id, MIN(artist) AS name, COUNT(DISTINCT albumId) AS albumCount,
                  COUNT(*) AS trackCount
           FROM tracks GROUP BY artistId ORDER BY name COLLATE NOCASE ASC""",
    )
    fun observeArtists(): Flow<List<ArtistRow>>

    @Query(
        """SELECT folderPath AS path, COUNT(*) AS trackCount
           FROM tracks GROUP BY folderPath ORDER BY path COLLATE NOCASE ASC""",
    )
    fun observeFolders(): Flow<List<FolderRow>>

    @Query("SELECT id FROM tracks")
    suspend fun allIds(): List<Long>

    @Upsert
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun count(): Int
}
