package com.radillo.playerwave.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radillo.playerwave.R
import com.radillo.playerwave.core.model.Track
import com.radillo.playerwave.data.collections.PlaylistRepository
import com.radillo.playerwave.player.engine.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PlaylistRepository,
    private val playback: PlaybackController,
) : ViewModel() {

    val playlistId: Long = checkNotNull(savedStateHandle.get<String>(ARG_ID)).toLong()
    val isFavorites: Boolean = playlistId == FAVORITES_PLAYLIST_ID

    val name: StateFlow<String?> = (if (isFavorites) flowOf(null) else repository.playlist(playlistId).map { it?.name })
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val tracks: StateFlow<List<Track>> = (if (isFavorites) repository.favoriteTracks else repository.playlistTracks(playlistId))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val nowPlayingId: StateFlow<Long?> =
        playback.state.map { it.currentTrack?.id }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun play(index: Int) = playback.playQueue(tracks.value, index)
    fun shuffle() {
        val list = tracks.value
        if (list.isEmpty()) return
        playback.setShuffle(true)
        playback.playQueue(list, list.indices.random())
    }

    fun remove(track: Track) {
        viewModelScope.launch {
            if (isFavorites) repository.toggleFavorite(track.id) else repository.removeFromPlaylist(playlistId, track.id)
        }
    }

    fun move(track: Track, delta: Int) {
        if (isFavorites) return
        viewModelScope.launch { repository.moveInPlaylist(playlistId, track.id, delta) }
    }

    companion object {
        const val ARG_ID = "playlistId"
    }
}
