package com.radillo.playerwave.ui.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radillo.playerwave.R
import com.radillo.playerwave.core.model.Album
import com.radillo.playerwave.core.model.Artist
import com.radillo.playerwave.core.model.Folder
import com.radillo.playerwave.core.model.Playlist
import com.radillo.playerwave.core.model.Track
import com.radillo.playerwave.data.collections.PlaylistRepository
import com.radillo.playerwave.data.library.LibraryRepository
import com.radillo.playerwave.data.library.ScanResult
import com.radillo.playerwave.player.engine.PlaybackController
import com.radillo.playerwave.ui.common.Row
import com.radillo.playerwave.ui.common.toRows
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Lo que muestra la biblioteca para la pestaña actual, ya convertido en filas. */
data class LibraryContent(
    val tab: LibraryTab,
    val rows: List<Row>,
    val songCount: Int,
    val tracks: List<Track>,
    val albums: List<Album>,
    val artists: List<Artist>,
    val folders: List<Folder>,
    val playlists: List<Playlist>,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LibraryRepository,
    private val playlistRepository: PlaylistRepository,
    private val playback: PlaybackController,
) : ViewModel() {

    private val sharing = SharingStarted.WhileSubscribed(5_000)
    private val prefs = context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)

    val isScanning: StateFlow<Boolean> = repository.isScanning

    /** Pestaña seleccionada; se recuerda entre sesiones (la app vuelve a donde estabas). */
    val selectedTab = MutableStateFlow(
        LibraryTab.entries.firstOrNull { it.name == prefs.getString(KEY_TAB, null) } ?: LibraryTab.SONGS,
    )

    fun selectTab(tab: LibraryTab) {
        selectedTab.value = tab
        prefs.edit().putString(KEY_TAB, tab.name).apply()
    }

    /** Primera fila visible guardada por pestaña. */
    fun savedScroll(tab: LibraryTab): Int = prefs.getInt(KEY_SCROLL + tab.name, 0)
    fun saveScroll(tab: LibraryTab, position: Int) {
        prefs.edit().putInt(KEY_SCROLL + tab.name, position.coerceAtLeast(0)).apply()
    }

    private val nowPlayingId = playback.state.map { it.currentTrack?.id }.distinctUntilChanged()

    private data class Sources(
        val tracks: List<Track>,
        val albums: List<Album>,
        val artists: List<Artist>,
        val folders: List<Folder>,
        val playlists: List<Playlist>,
    )

    private val sources = combine(
        repository.tracks, repository.albums, repository.artists, repository.folders, playlistRepository.playlists,
    ) { t, al, ar, f, p -> Sources(t, al, ar, f, p) }

    /**
     * Una sola emisión por cambio, con las filas construidas fuera del hilo principal: antes cada
     * flujo provocaba su propio repintado (8 al abrir) y convertía miles de canciones en el hilo de UI.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val content: StateFlow<LibraryContent?> = combine(
        selectedTab, sources, playlistRepository.favoriteCount, nowPlayingId,
    ) { tab, src, favorites, nowPlaying -> Triple(tab, src, favorites to nowPlaying) }
        .mapLatest { (tab, src, extra) ->
            val (favorites, nowPlaying) = extra
            LibraryContent(
                tab = tab,
                rows = buildRows(tab, src, favorites, nowPlaying),
                songCount = src.tracks.size,
                tracks = src.tracks,
                albums = src.albums,
                artists = src.artists,
                folders = src.folders,
                playlists = src.playlists,
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, sharing, null)

    private fun buildRows(tab: LibraryTab, src: Sources, favoriteCount: Int, nowPlaying: Long?): List<Row> {
        val res = context.resources
        fun tracks(n: Int) = res.getQuantityString(R.plurals.track_count, n, n)
        return when (tab) {
            LibraryTab.SONGS -> src.tracks.toRows(context, nowPlaying)
            LibraryTab.ALBUMS -> src.albums.map { a ->
                Row(
                    key = "a${a.id}",
                    title = a.title.ifBlank { context.getString(R.string.unknown_album) },
                    subtitle = listOf(a.artist, tracks(a.trackCount)).filter { it.isNotBlank() }.joinToString(" · "),
                    artUri = a.albumArtUri,
                    placeholder = R.drawable.tile_album,
                    showMore = false,
                )
            }
            LibraryTab.ARTISTS -> src.artists.map { a ->
                Row(
                    key = "r${a.id}",
                    title = a.name.ifBlank { context.getString(R.string.unknown_artist) },
                    subtitle = res.getQuantityString(R.plurals.album_count, a.albumCount, a.albumCount) + " · " + tracks(a.trackCount),
                    artUri = null,
                    placeholder = R.drawable.tile_person,
                    showMore = false,
                )
            }
            LibraryTab.FOLDERS -> src.folders.map { f ->
                Row(
                    key = "f${f.path}",
                    title = f.name,
                    // La ruta ayuda a distinguir carpetas con el mismo nombre en la USB y en la memoria.
                    subtitle = tracks(f.trackCount) + " · " + f.path.substringBeforeLast('/').substringAfterLast('/'),
                    artUri = null,
                    placeholder = R.drawable.tile_folder,
                    showMore = false,
                )
            }
            LibraryTab.PLAYLISTS -> buildList {
                add(Row("fav", context.getString(R.string.favorites_title), tracks(favoriteCount), null, R.drawable.tile_favorite, showMore = false))
                add(Row("new", context.getString(R.string.playlist_new), context.getString(R.string.playlist_new_hint), null, R.drawable.tile_add, showMore = false))
                src.playlists.forEach { p -> add(Row("p${p.id}", p.name, tracks(p.trackCount), null, R.drawable.tile_queue)) }
            }
        }
    }

    /** Resultado de una búsqueda manual, para avisar «12 canciones nuevas» o «sin cambios». */
    private val _scanResults = MutableSharedFlow<ScanResult>(extraBufferCapacity = 1)
    val scanResults: SharedFlow<ScanResult> = _scanResults
    private var scannedOnce = false

    /** Botón «Buscar música nueva»: lectura completa de MediaStore aunque la huella no cambie. */
    fun rescan() {
        viewModelScope.launch { _scanResults.emit(repository.rescan(force = true)) }
    }

    /**
     * Al abrir la biblioteca: la caché se muestra al instante y solo se escanea si MediaStore
     * cambió desde la última vez (una comprobación de milisegundos). Una vez por proceso.
     */
    fun ensureScanned() {
        if (scannedOnce) return
        scannedOnce = true
        viewModelScope.launch { repository.rescan(force = false) }
    }

    fun playAll(tracks: List<Track>, startIndex: Int) = playback.playQueue(tracks, startIndex)

    fun shuffleAll(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        playback.setShuffle(true)
        playback.playQueue(tracks, tracks.indices.random())
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch { playlistRepository.createPlaylist(name) }
    }

    fun renamePlaylist(id: Long, name: String) {
        viewModelScope.launch { playlistRepository.renamePlaylist(id, name) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { playlistRepository.deletePlaylist(id) }
    }

    companion object {
        const val UI_PREFS = "ui_state"
        private const val KEY_TAB = "library_tab"
        private const val KEY_SCROLL = "library_scroll_"
    }
}

/** Id reservado para la lista virtual de favoritos. */
const val FAVORITES_PLAYLIST_ID = -1L

/** Pestañas de la biblioteca. */
enum class LibraryTab { SONGS, ALBUMS, ARTISTS, FOLDERS, PLAYLISTS }
