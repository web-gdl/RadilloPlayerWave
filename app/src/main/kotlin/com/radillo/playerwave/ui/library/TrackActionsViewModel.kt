package com.radillo.playerwave.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radillo.playerwave.core.model.Playlist
import com.radillo.playerwave.core.model.Track
import com.radillo.playerwave.data.collections.PlaylistRepository
import com.radillo.playerwave.player.engine.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Acciones comunes sobre una pista: cola, favoritos y listas. Compartido por todas las pantallas. */
@HiltViewModel
class TrackActionsViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
    private val playback: PlaybackController,
) : ViewModel() {

    val favoriteIds: StateFlow<Set<Long>> =
        playlists.favoriteIds.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val playlistList: StateFlow<List<Playlist>> =
        playlists.playlists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun playNext(track: Track) = playback.playNext(listOf(track))
    fun addToQueue(track: Track) = playback.addToQueue(listOf(track))
    fun toggleFavorite(track: Track) {
        viewModelScope.launch { playlists.toggleFavorite(track.id) }
    }

    fun addToPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch { playlists.addToPlaylist(playlistId, listOf(track.id)) }
    }

    fun createPlaylistWith(name: String, track: Track) {
        viewModelScope.launch {
            val id = playlists.createPlaylist(name)
            playlists.addToPlaylist(id, listOf(track.id))
        }
    }
}
