package com.radillo.playerwave.data.backup

import androidx.room.withTransaction
import com.radillo.playerwave.data.library.db.FavoriteEntity
import com.radillo.playerwave.data.library.db.PlaylistEntity
import com.radillo.playerwave.data.library.db.PlaylistTrackEntity
import com.radillo.playerwave.data.library.db.RadilloDatabase

/** Resultado de aplicar un respaldo: lo que se recuperó y lo que queda pendiente. */
data class RestoreResult(
    val favoritesAdded: Int,
    val playlistsCreated: Int,
    val tracksAdded: Int,
    val pending: CollectionsSnapshot,
)

/** Lee favoritos y listas de la base de datos y aplica respaldos sobre ella (sin borrar nada). */
class CollectionsRestorer(private val database: RadilloDatabase) {

    private val playlists = database.playlistDao()
    private val tracks = database.trackDao()

    /**
     * Foto de lo que hay en la app. Si una canción no está ahora en la biblioteca (USB quitada),
     * se describe con lo que decía el respaldo anterior, para no perderla.
     */
    suspend fun snapshot(previous: CollectionsSnapshot = CollectionsSnapshot.EMPTY): CollectionsSnapshot =
        database.withTransaction {
            val known = tracks.allEntities().associateBy { it.id }
            val previousById = (previous.favorites + previous.playlists.flatMap { it.tracks })
                .filter { it.id >= 0 }
                .associateBy { it.id }
            fun describe(id: Long, addedAtMs: Long): BackupTrack? =
                known[id]?.let { BackupTrack.of(it, addedAtMs) } ?: previousById[id]?.copy(addedAtMs = addedAtMs)

            val favorites = playlists.allFavorites().mapNotNull { describe(it.trackId, it.addedAtMs) }
            val lists = playlists.allPlaylists().map { p ->
                BackupPlaylist(p.name, p.createdAtMs, playlists.playlistTrackIds(p.id).mapNotNull { describe(it, 0L) })
            }
            CollectionsSnapshot(favorites, lists)
        }

    /**
     * Agrega a la app lo que se encuentre del respaldo: favoritos que falten y canciones al final de
     * la lista del mismo nombre (se crea si no existe). Con [createEmpty] se crean también las listas
     * de las que aún no se encontró ninguna canción (restauración inicial o importación a mano).
     */
    suspend fun apply(backup: CollectionsSnapshot, createEmpty: Boolean): RestoreResult = database.withTransaction {
        val matcher = TrackMatcher(tracks.allEntities())
        val now = System.currentTimeMillis()

        var favoritesAdded = 0
        val leftFavorites = ArrayList<BackupTrack>()
        backup.favorites.forEach { t ->
            val id = matcher.find(t)
            when {
                id == null -> leftFavorites += t
                !playlists.isFavorite(id) -> {
                    playlists.insertFavorite(FavoriteEntity(id, t.addedAtMs.takeIf { it > 0 } ?: now))
                    favoritesAdded++
                }
            }
        }

        var playlistsCreated = 0
        var tracksAdded = 0
        val leftPlaylists = ArrayList<BackupPlaylist>()
        backup.playlists.forEach { p ->
            val matched = p.tracks.map { it to matcher.find(it) }
            val found = matched.mapNotNull { it.second }.distinct()
            val missing = matched.filter { it.second == null }.map { it.first }
            if (found.isNotEmpty() || createEmpty) {
                val playlistId = playlists.playlistIdByName(p.name) ?: run {
                    playlistsCreated++
                    playlists.insertPlaylist(PlaylistEntity(name = p.name, createdAtMs = p.createdAtMs.takeIf { it > 0 } ?: now))
                }
                val existing = playlists.playlistTrackIds(playlistId).toHashSet()
                var position = playlists.maxPosition(playlistId)
                val entries = found.filterNot { it in existing }.map { PlaylistTrackEntity(playlistId, it, ++position) }
                if (entries.isNotEmpty()) playlists.insertPlaylistTracks(entries)
                tracksAdded += entries.size
            }
            if (missing.isNotEmpty()) leftPlaylists += p.copy(tracks = missing)
        }
        RestoreResult(favoritesAdded, playlistsCreated, tracksAdded, CollectionsSnapshot(leftFavorites, leftPlaylists))
    }

    /** Quita de lo pendiente las listas que el usuario ya borró de la app. */
    suspend fun prunePending(pending: CollectionsSnapshot): CollectionsSnapshot {
        if (pending.playlists.isEmpty()) return pending
        val names = playlists.allPlaylists().map { it.name.trim().lowercase() }.toHashSet()
        return pending.copy(playlists = pending.playlists.filter { it.name.trim().lowercase() in names })
    }
}
