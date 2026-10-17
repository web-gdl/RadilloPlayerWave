package com.radillo.playerwave.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radillo.playerwave.core.model.Album
import com.radillo.playerwave.core.model.Artist
import com.radillo.playerwave.core.model.Folder
import com.radillo.playerwave.core.model.Playlist
import com.radillo.playerwave.core.model.Track
import com.radillo.playerwave.data.collections.PlaylistRepository
import com.radillo.playerwave.data.library.LibraryRepository
import com.radillo.playerwave.player.engine.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import com.radillo.playerwave.data.library.ScanResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val playlistRepository: PlaylistRepository,
    private val playback: PlaybackController,
) : ViewModel() {

    private val sharing = SharingStarted.WhileSubscribed(5_000)

    val tracks: StateFlow<List<Track>> = repository.tracks.stateIn(viewModelScope, sharing, emptyList())
    val albums: StateFlow<List<Album>> = repository.albums.stateIn(viewModelScope, sharing, emptyList())
    val artists: StateFlow<List<Artist>> = repository.artists.stateIn(viewModelScope, sharing, emptyList())
    val folders: StateFlow<List<Folder>> = repository.folders.stateIn(viewModelScope, sharing, emptyList())
    val playlists: StateFlow<List<Playlist>> = playlistRepository.playlists.stateIn(viewModelScope, sharing, emptyList())
    val favoriteCount: StateFlow<Int> = playlistRepository.favoriteCount.stateIn(viewModelScope, sharing, 0)
    val isScanning: StateFlow<Boolean> = repository.isScanning
    val nowPlayingId: StateFlow<Long?> =
        playback.state.map { it.currentTrack?.id }.stateIn(viewModelScope, sharing, null)

    /** Pestaña seleccionada (sobrevive a rotaciones). */
    val selectedTab = MutableStateFlow(LibraryTab.SONGS)
    private var scannedOnce = false

    /** Resultado de una búsqueda manual, para avisar «12 canciones nuevas» o «sin cambios». */
    private val _scanResults = MutableSharedFlow<ScanResult>(extraBufferCapacity = 1)
    val scanResults: SharedFlow<ScanResult> = _scanResults

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
}

/** Id reservado para la lista virtual de favoritos. */
const val FAVORITES_PLAYLIST_ID = -1L

/** Pestañas de la biblioteca. */
enum class LibraryTab { SONGS, ALBUMS, ARTISTS, FOLDERS, PLAYLISTS }
