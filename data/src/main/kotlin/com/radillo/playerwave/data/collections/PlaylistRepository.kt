package com.radillo.playerwave.data.collections

import androidx.room.withTransaction
import com.radillo.playerwave.core.model.Playlist
import com.radillo.playerwave.core.model.Track
import com.radillo.playerwave.data.library.db.FavoriteEntity
import com.radillo.playerwave.data.library.db.PlaylistEntity
import com.radillo.playerwave.data.library.db.PlaylistTrackEntity
import com.radillo.playerwave.data.library.db.RadilloDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Favoritos y listas de reproducción del usuario. */
interface PlaylistRepository {
    val favoriteIds: Flow<Set<Long>>
    val favoriteTracks: Flow<List<Track>>
    val favoriteCount: Flow<Int>
    val playlists: Flow<List<Playlist>>

    fun playlist(id: Long): Flow<Playlist?>
    fun playlistTracks(id: Long): Flow<List<Track>>

    /** Devuelve el nuevo estado (true = favorita). */
    suspend fun toggleFavorite(trackId: Long): Boolean
    suspend fun createPlaylist(name: String): Long
    suspend fun renamePlaylist(id: Long, name: String)
    suspend fun deletePlaylist(id: Long)
    suspend fun addToPlaylist(playlistId: Long, trackIds: List<Long>)
    suspend fun removeFromPlaylist(playlistId: Long, trackId: Long)
    /** Mueve la pista `delta` posiciones (negativo = hacia arriba). */
    suspend fun moveInPlaylist(playlistId: Long, trackId: Long, delta: Int)
}

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val database: RadilloDatabase,
) : PlaylistRepository {

    private val dao = database.playlistDao()

    override val favoriteIds: Flow<Set<Long>> = dao.observeFavoriteIds().map { it.toHashSet() }
    override val favoriteTracks: Flow<List<Track>> = dao.observeFavoriteTracks().map { list -> list.map { it.toTrack() } }
    override val favoriteCount: Flow<Int> = dao.observeFavoriteCount()
    override val playlists: Flow<List<Playlist>> = dao.observePlaylists().map { rows ->
        rows.map { Playlist(it.id, it.name, it.trackCount, it.createdAtMs) }
    }

    override fun playlist(id: Long): Flow<Playlist?> =
        dao.observePlaylist(id).map { e -> e?.let { Playlist(it.id, it.name, 0, it.createdAtMs) } }

    override fun playlistTracks(id: Long): Flow<List<Track>> =
        dao.observePlaylistTracks(id).map { list -> list.map { it.toTrack() } }

    override suspend fun toggleFavorite(trackId: Long): Boolean {
        return database.withTransaction {
            if (dao.isFavorite(trackId)) {
                dao.deleteFavorite(trackId)
                false
            } else {
                dao.insertFavorite(FavoriteEntity(trackId, System.currentTimeMillis()))
                true
            }
        }
    }

    override suspend fun createPlaylist(name: String): Long =
        dao.insertPlaylist(PlaylistEntity(name = name.trim(), createdAtMs = System.currentTimeMillis()))

    override suspend fun renamePlaylist(id: Long, name: String) = dao.renamePlaylist(id, name.trim())

    override suspend fun deletePlaylist(id: Long) {
        database.withTransaction {
            dao.deletePlaylistTracks(id)
            dao.deletePlaylist(id)
        }
    }

    override suspend fun addToPlaylist(playlistId: Long, trackIds: List<Long>) {
        if (trackIds.isEmpty()) return
        database.withTransaction {
            val existing = dao.playlistTrackIds(playlistId).toHashSet()
            var position = dao.maxPosition(playlistId)
            val entries = trackIds.filterNot { it in existing }.distinct().map { trackId ->
                position++
                PlaylistTrackEntity(playlistId, trackId, position)
            }
            if (entries.isNotEmpty()) dao.insertPlaylistTracks(entries)
        }
    }

    override suspend fun removeFromPlaylist(playlistId: Long, trackId: Long) {
        database.withTransaction {
            dao.deletePlaylistTrack(playlistId, trackId)
            renumber(playlistId)
        }
    }

    override suspend fun moveInPlaylist(playlistId: Long, trackId: Long, delta: Int) {
        if (delta == 0) return
        database.withTransaction {
            val ids = dao.playlistTrackIds(playlistId).toMutableList()
            val from = ids.indexOf(trackId)
            if (from < 0) return@withTransaction
            val to = (from + delta).coerceIn(0, ids.lastIndex)
            if (to == from) return@withTransaction
            ids.removeAt(from)
            ids.add(to, trackId)
            ids.forEachIndexed { index, id -> dao.updatePosition(playlistId, id, index) }
        }
    }

    private suspend fun renumber(playlistId: Long) {
        dao.playlistTrackIds(playlistId).forEachIndexed { index, id -> dao.updatePosition(playlistId, id, index) }
    }
}
