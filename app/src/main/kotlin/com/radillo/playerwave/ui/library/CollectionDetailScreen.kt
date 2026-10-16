package com.radillo.playerwave.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.radillo.playerwave.R
import com.radillo.playerwave.core.model.Track
import com.radillo.playerwave.core.ui.components.ArtworkImage
import com.radillo.playerwave.data.library.LibraryRepository
import com.radillo.playerwave.player.engine.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Tipo de colección mostrada en la pantalla de detalle. Los valores son parte de la ruta de navegación. */
enum class CollectionKind { ALBUM, ARTIST, FOLDER }

@HiltViewModel
class CollectionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: LibraryRepository,
    private val playback: PlaybackController,
) : ViewModel() {

    val kind: CollectionKind = CollectionKind.valueOf(checkNotNull(savedStateHandle.get<String>(ARG_KIND)))
    val title: String = savedStateHandle.get<String>(ARG_TITLE).orEmpty()
    private val key: String = checkNotNull(savedStateHandle.get<String>(ARG_KEY))

    val tracks: StateFlow<List<Track>> = when (kind) {
        CollectionKind.ALBUM -> repository.tracksOfAlbum(key.toLong())
        CollectionKind.ARTIST -> repository.tracksOfArtist(key.toLong())
        CollectionKind.FOLDER -> repository.tracksOfFolder(key)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val nowPlayingId: StateFlow<Long?> =
        playback.state.map { it.currentTrack?.id }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun play(index: Int) = playback.playQueue(tracks.value, index)
    fun shuffle() {
        val list = tracks.value
        if (list.isEmpty()) return
        playback.setShuffle(true)
        playback.playQueue(list, list.indices.random())
    }
    companion object {
        const val ARG_KIND = "kind"
        const val ARG_KEY = "key"
        const val ARG_TITLE = "title"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    onBack: () -> Unit,
    viewModel: CollectionDetailViewModel = hiltViewModel(),
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val nowPlayingId by viewModel.nowPlayingId.collectAsStateWithLifecycle()
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    val title = viewModel.title.ifBlank {
        stringResource(if (viewModel.kind == CollectionKind.ARTIST) R.string.unknown_artist else R.string.unknown_album)
    }

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
        val sample = tracks.firstOrNull()
        val header: (@Composable () -> Unit)? =
            if (viewModel.kind == CollectionKind.ALBUM && sample != null) {
                { AlbumHeader(sample) }
            } else {
                null
            }
        Column(Modifier.padding(padding).fillMaxSize()) {
            SongsList(
                tracks = tracks,
                nowPlayingId = nowPlayingId,
                onPlay = viewModel::play,
                onShuffle = viewModel::shuffle,
                onMenu = { menuTrack = it },
                header = header,
            )
        }
    }

    menuTrack?.let { track ->
        TrackActionsSheet(track = track, onDismiss = { menuTrack = null })
    }
}

@Composable
private fun AlbumHeader(sample: Track) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(model = sample.albumArtUri, modifier = Modifier.size(120.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                sample.album.ifBlank { stringResource(R.string.unknown_album) },
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                sample.artist.ifBlank { stringResource(R.string.unknown_artist) },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (sample.year > 0) {
                Text(
                    sample.year.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
