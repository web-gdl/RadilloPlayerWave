package com.radillo.playerwave.data.collections

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.radillo.playerwave.data.library.db.RadilloDatabase
import com.radillo.playerwave.data.library.db.TrackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pruebas de integración de favoritos y listas sobre una base de datos Room en memoria. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaylistRepositoryTest {

    private lateinit var db: RadilloDatabase
    private lateinit var repo: PlaylistRepositoryImpl

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RadilloDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = PlaylistRepositoryImpl(db)
        db.trackDao().upsertAll((1L..5L).map { track(it) })
    }

    @After
    fun tearDown() = db.close()

    private fun track(id: Long) = TrackEntity(
        id = id, uri = "content://media/external/audio/media/$id", title = "Pista $id", artist = "A", artistId = 1,
        album = "Al", albumId = 1, durationMs = 60_000, trackNumber = id.toInt(), year = 2020,
        folderPath = "/m", fileName = "$id.mp3", mimeType = "audio/mpeg", dateAddedSec = 0, sizeBytes = 1,
    )

    @Test
    fun `favoritos se alternan y se observan`() = runBlocking {
        assertThat(repo.toggleFavorite(2)).isTrue()
        assertThat(repo.toggleFavorite(4)).isTrue()
        assertThat(repo.favoriteIds.first()).containsExactly(2L, 4L)
        assertThat(repo.favoriteTracks.first().map { it.id }).containsExactly(2L, 4L)
        assertThat(repo.toggleFavorite(2)).isFalse()
        assertThat(repo.favoriteCount.first()).isEqualTo(1)
    }

    @Test
    fun `las listas conservan el orden, evitan duplicados y se reordenan`() = runBlocking {
        val id = repo.createPlaylist("  Viaje ")
        assertThat(repo.playlists.first().single().name).isEqualTo("Viaje")

        repo.addToPlaylist(id, listOf(3, 1, 5))
        repo.addToPlaylist(id, listOf(1, 2)) // 1 ya está: solo entra 2
        assertThat(repo.playlistTracks(id).first().map { it.id }).containsExactly(3L, 1L, 5L, 2L).inOrder()
        assertThat(repo.playlists.first().single().trackCount).isEqualTo(4)

        repo.moveInPlaylist(id, 5, -2)
        assertThat(repo.playlistTracks(id).first().map { it.id }).containsExactly(5L, 3L, 1L, 2L).inOrder()

        repo.removeFromPlaylist(id, 3)
        assertThat(repo.playlistTracks(id).first().map { it.id }).containsExactly(5L, 1L, 2L).inOrder()

        repo.renamePlaylist(id, "Viaje largo")
        assertThat(repo.playlist(id).first()?.name).isEqualTo("Viaje largo")

        repo.deletePlaylist(id)
        assertThat(repo.playlists.first()).isEmpty()
        assertThat(repo.playlistTracks(id).first()).isEmpty()
    }
}
