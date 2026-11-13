package com.radillo.playerwave.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.radillo.playerwave.data.collections.PlaylistRepositoryImpl
import com.radillo.playerwave.data.library.db.RadilloDatabase
import com.radillo.playerwave.data.library.db.TrackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Respaldo de favoritos y listas: que sobreviva a reinstalar aunque cambien los ids de MediaStore. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CollectionsBackupTest {

    private val dbs = mutableListOf<RadilloDatabase>()

    @After
    fun tearDown() = dbs.forEach { it.close() }

    private fun newDb(): RadilloDatabase =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RadilloDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { dbs += it }

    private fun track(id: Long, name: String, folder: String = "/storage/usb/musica", durationMs: Long = 200_000) = TrackEntity(
        id = id, uri = "content://media/external/audio/media/$id", title = name, artist = "Artista $name", artistId = 1,
        album = "Al", albumId = 1, durationMs = durationMs, trackNumber = 1, year = 2020,
        folderPath = folder, fileName = "$name.mp3", mimeType = "audio/mpeg", dateAddedSec = 0, sizeBytes = 1,
    )

    @Test
    fun `reinstalar con ids nuevos recupera favoritos y listas en orden`(): Unit = runBlocking {
        // Instalación anterior.
        val old = newDb()
        old.trackDao().upsertAll(listOf(track(1, "a"), track(2, "b"), track(3, "c")))
        val oldRepo = PlaylistRepositoryImpl(old)
        oldRepo.toggleFavorite(2)
        val list = oldRepo.createPlaylist("Carretera")
        oldRepo.addToPlaylist(list, listOf(3, 1))
        oldRepo.createPlaylist("Vacía")
        val text = BackupCodec.encode(BackupFile(CollectionsRestorer(old).snapshot()), 0)

        // Instalación nueva: el sistema reconstruyó su base y las mismas canciones tienen otros ids.
        val fresh = newDb()
        fresh.trackDao().upsertAll(listOf(track(11, "a"), track(12, "b"), track(13, "c")))
        val decoded = BackupCodec.decode(text)!!
        val result = CollectionsRestorer(fresh).apply(decoded.everything, createEmpty = true)

        assertThat(result.pending.isEmpty).isTrue()
        val repo = PlaylistRepositoryImpl(fresh)
        assertThat(repo.favoriteIds.first()).containsExactly(12L)
        val playlists = repo.playlists.first()
        assertThat(playlists.map { it.name }).containsExactly("Carretera", "Vacía")
        val carretera = playlists.first { it.name == "Carretera" }
        assertThat(repo.playlistTracks(carretera.id).first().map { it.id }).containsExactly(13L, 11L).inOrder()
    }

    @Test
    fun `lo que no esta queda pendiente y se agrega cuando aparece sin duplicar`(): Unit = runBlocking {
        val db = newDb()
        db.trackDao().upsertAll(listOf(track(1, "a")))
        val backup = CollectionsSnapshot(
            favorites = listOf(BackupTrack(50, "/storage/usb/musica/a.mp3", "a", "Artista a", 200_000), BackupTrack(51, "/otra/usb/b.mp3", "b", "Artista b", 200_000)),
            playlists = listOf(BackupPlaylist("Ruta", 1, listOf(BackupTrack(51, "/otra/usb/b.mp3", "b", "Artista b", 200_000)))),
        )
        val restorer = CollectionsRestorer(db)
        val first = restorer.apply(backup, createEmpty = true)
        assertThat(first.favoritesAdded).isEqualTo(1)
        assertThat(first.playlistsCreated).isEqualTo(1)
        assertThat(first.pending.trackCount).isEqualTo(2)

        // Se pone la USB: «b» aparece en otra ruta, se reconoce por título, artista y duración.
        db.trackDao().upsertAll(listOf(track(2, "b", folder = "/storage/1234-ABCD", durationMs = 201_000)))
        val second = restorer.apply(first.pending, createEmpty = false)
        assertThat(second.pending.isEmpty).isTrue()
        assertThat(second.playlistsCreated).isEqualTo(0)
        val repo = PlaylistRepositoryImpl(db)
        assertThat(repo.favoriteIds.first()).containsExactly(1L, 2L)

        // Aplicar otra vez el mismo respaldo no duplica nada.
        val again = restorer.apply(backup, createEmpty = true)
        assertThat(again.favoritesAdded + again.playlistsCreated + again.tracksAdded).isEqualTo(0)
    }

    @Test
    fun `la foto conserva canciones de una USB quitada usando el respaldo anterior`(): Unit = runBlocking {
        val db = newDb()
        db.trackDao().upsertAll(listOf(track(1, "a"), track(2, "b")))
        val repo = PlaylistRepositoryImpl(db)
        repo.toggleFavorite(1)
        repo.toggleFavorite(2)
        val restorer = CollectionsRestorer(db)
        val before = restorer.snapshot()
        db.trackDao().deleteByIds(listOf(2)) // se quitó la USB: el favorito sigue, la canción no
        val after = restorer.snapshot(previous = before)
        assertThat(after.favorites.map { it.title }).containsExactly("a", "b")
    }

    @Test
    fun `codec ida y vuelta y rechaza otros archivos`() {
        val file = BackupFile(
            saved = CollectionsSnapshot(listOf(BackupTrack(1, "/m/a.mp3", "Ñandú «x»", "Ártista", 1000, 5))),
            pending = CollectionsSnapshot(playlists = listOf(BackupPlaylist("Lista", 2, listOf(BackupTrack(2, "/m/b.mp3", "b", "c", 3))))),
        )
        assertThat(BackupCodec.decode(BackupCodec.encode(file, 99))).isEqualTo(file)
        assertThat(BackupCodec.decode("{\"hola\":1}")).isNull()
        assertThat(BackupCodec.decode("no es json")).isNull()
    }
}
