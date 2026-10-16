package com.radillo.playerwave.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val name by viewModel.name.collectAsStateWithLifecycle()
    val nowPlayingId by viewModel.nowPlayingId.collectAsStateWithLifecycle()
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    val title = if (viewModel.isFavorites) stringResource(R.string.favorites_title) else name.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SongsList(
                tracks = tracks,
                nowPlayingId = nowPlayingId,
                onPlay = viewModel::play,
                onShuffle = viewModel::shuffle,
                onMenu = { menuTrack = it },
                emptyText = stringResource(if (viewModel.isFavorites) R.string.favorites_empty else R.string.playlist_empty),
            )
        }
    }

    menuTrack?.let { track ->
        val extras = buildList {
            if (!viewModel.isFavorites) {
                add(SheetAction(R.string.action_move_up, Icons.Rounded.ArrowUpward) { viewModel.move(track, -1) })
                add(SheetAction(R.string.action_move_down, Icons.Rounded.ArrowDownward) { viewModel.move(track, 1) })
            }
            add(
                SheetAction(
                    if (viewModel.isFavorites) R.string.action_remove_favorite else R.string.action_remove_from_playlist,
                    Icons.Rounded.Delete,
                ) { viewModel.remove(track) },
            )
        }
        TrackActionsSheet(track = track, onDismiss = { menuTrack = null }, extraActions = extras)
    }
}
