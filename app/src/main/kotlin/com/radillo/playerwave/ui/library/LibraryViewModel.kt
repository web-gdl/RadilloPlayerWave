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

    fun rescan() {
        viewModelScope.launch { repository.rescan() }
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
