package com.radillo.playerwave.data.library

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.room.withTransaction
import com.radillo.playerwave.core.model.Album
import com.radillo.playerwave.core.model.Artist
import com.radillo.playerwave.core.model.Folder
import com.radillo.playerwave.core.model.Track
import com.radillo.playerwave.data.library.db.RadilloDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val database: RadilloDatabase,
    private val scanner: MediaStoreScanner,
    private val contentResolver: ContentResolver,
) : LibraryRepository {

    private val dao = database.trackDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scanMutex = Mutex()
    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning

    private val changeSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            changeSignal.tryEmit(Unit)
        }
    }

    init {
        // Cambios en MediaStore (copiar o borrar archivos) → reescaneo con un pequeño retardo.
        contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        @OptIn(FlowPreview::class)
        scope.launch {
            changeSignal.debounce(1_500).collect { rescan() }
        }
    }

    override val tracks: Flow<List<Track>> = dao.observeAll().map { list -> list.map { it.toTrack() } }

    override val albums: Flow<List<Album>> = dao.observeAlbums().map { rows ->
        rows.map { Album(it.id, it.title, it.artist, it.artistId, it.trackCount, it.year) }
    }

    override val artists: Flow<List<Artist>> = dao.observeArtists().map { rows ->
        rows.map { Artist(it.id, it.name, it.albumCount, it.trackCount) }
    }

    override val folders: Flow<List<Folder>> = dao.observeFolders().map { rows ->
        rows.map { Folder(it.path, FolderPath.displayName(it.path), it.trackCount) }
    }

    override fun tracksOfAlbum(albumId: Long): Flow<List<Track>> =
        dao.observeByAlbum(albumId).map { list -> list.map { it.toTrack() } }

    override fun tracksOfArtist(artistId: Long): Flow<List<Track>> =
        dao.observeByArtist(artistId).map { list -> list.map { it.toTrack() } }

    override fun tracksOfFolder(folderPath: String): Flow<List<Track>> =
        dao.observeByFolder(folderPath).map { list -> list.map { it.toTrack() } }

    override fun search(query: String): Flow<List<Track>> =
        dao.search(query.trim()).map { list -> list.map { it.toTrack() } }

    override suspend fun rescan() {
        // Si ya hay un escaneo en curso, el segundo espera y repite (los datos pueden haber cambiado).
        scanMutex.withLock {
            _isScanning.value = true
            try {
                val scanned = scanner.scan()
                val scannedIds = scanned.mapTo(HashSet()) { it.id }
                database.withTransaction {
                    val stale = dao.allIds().filterNot { it in scannedIds }
                    // SQLite limita las variables por consulta: se trabaja por lotes.
                    stale.chunked(BATCH).forEach { dao.deleteByIds(it) }
                    scanned.chunked(BATCH).forEach { dao.upsertAll(it) }
                }
            } finally {
                _isScanning.value = false
            }
        }
    }

    private companion object {
        const val BATCH = 500
    }
}
