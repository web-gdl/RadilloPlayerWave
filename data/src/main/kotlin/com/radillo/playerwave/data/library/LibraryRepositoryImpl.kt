package com.radillo.playerwave.data.library

import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import com.radillo.playerwave.core.diagnostics.EventLog
import com.radillo.playerwave.core.model.Album
import com.radillo.playerwave.core.model.Artist
import com.radillo.playerwave.core.model.Folder
import com.radillo.playerwave.core.model.Track
import com.radillo.playerwave.data.library.db.RadilloDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
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

/**
 * Biblioteca: Room es la caché que se muestra al instante; MediaStore es la fuente de verdad.
 *
 * Arrancar ya no reescanea: se compara la [LibrarySignature] guardada con la actual (unos
 * milisegundos) y solo si difiere se lee MediaStore y se aplica el [LibraryDiff]. Los avisos de
 * MediaStore (copiar música, conectar una USB) se agrupan durante unos segundos para no escanear
 * decenas de veces mientras el sistema indexa una memoria.
 */
@Singleton
class LibraryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: RadilloDatabase,
    private val scanner: MediaStoreScanner,
    private val contentResolver: ContentResolver,
) : LibraryRepository {

    private val dao = database.trackDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scanMutex = Mutex()
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning

    private val changeSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            changeSignal.tryEmit(Unit)
        }
    }

    /** USB o tarjeta conectada/retirada, o fin del indexado del sistema. */
    private val mediaReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            EventLog.log("Almacenamiento: ${intent.action?.substringAfterLast('.')}")
            changeSignal.tryEmit(Unit)
        }
    }

    init {
        contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        registerMediaReceiver()
        @OptIn(FlowPreview::class)
        scope.launch {
            changeSignal.debounce(CHANGE_DEBOUNCE_MS).collect {
                try {
                    rescan(force = false)
                } catch (e: Exception) {
                    EventLog.log("Error al sincronizar la biblioteca: ${e.javaClass.simpleName}")
                }
            }
        }
    }

    private fun registerMediaReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            @Suppress("DEPRECATION")
            addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED)
            addDataScheme("file")
        }
        try {
            // Exportado: «fin del escaneo» lo envía MediaProvider (no el sistema) y, por debajo de
            // Android 13, un receptor no exportado no lo recibiría.
            ContextCompat.registerReceiver(context, mediaReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        } catch (e: Exception) {
            // Sin receptor seguimos teniendo el ContentObserver.
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

    override suspend fun rescan(force: Boolean): ScanResult = scanMutex.withLock {
        val signature = scanner.signature()
        val cachedCount = dao.count()
        if (!force && signature != null && cachedCount > 0 &&
            signature == LibrarySignature.decode(prefs.getString(KEY_SIGNATURE, null))
        ) {
            return@withLock ScanResult.unchanged(cachedCount)
        }
        _isScanning.value = true
        try {
            val started = System.currentTimeMillis()
            val scanned = scanner.scan()
            val diff = LibraryDiff.compute(dao.allEntities(), scanned)
            if (diff.upserts.isNotEmpty() || diff.deletes.isNotEmpty()) {
                database.withTransaction {
                    // SQLite limita las variables por consulta: se trabaja por lotes.
                    diff.deletes.chunked(BATCH).forEach { dao.deleteByIds(it) }
                    diff.upserts.chunked(BATCH).forEach { dao.upsertAll(it) }
                }
            }
            // La huella se guarda tras escanear: si el permiso faltaba (null), se reintentará.
            signature?.let { prefs.edit().putString(KEY_SIGNATURE, it.encode()).putLong(KEY_LAST_SCAN, System.currentTimeMillis()).apply() }
            val result = ScanResult(diff.added, diff.deletes.size, diff.updated, scanned.size)
            EventLog.log(
                "Biblioteca: ${result.total} canciones (+${result.added} −${result.removed} ~${result.updated}) " +
                    "en ${System.currentTimeMillis() - started} ms",
            )
            result
        } finally {
            _isScanning.value = false
        }
    }

    private companion object {
        const val BATCH = 500
        const val CHANGE_DEBOUNCE_MS = 3_000L
        const val PREFS = "library_state"
        const val KEY_SIGNATURE = "signature"
        const val KEY_LAST_SCAN = "last_scan_ms"
    }
}
