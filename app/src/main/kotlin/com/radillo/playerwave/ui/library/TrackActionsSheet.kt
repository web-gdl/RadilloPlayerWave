package com.radillo.playerwave.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.radillo.playerwave.R
import com.radillo.playerwave.core.model.Playlist
import com.radillo.playerwave.core.model.Track
import com.radillo.playerwave.core.ui.components.ArtworkThumb
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
        playlists.favoriteIds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    val playlistList: StateFlow<List<Playlist>> =
        playlists.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

/** Acción adicional que una pantalla puede añadir a la hoja (p. ej. "Quitar de la lista"). */
class SheetAction(val labelRes: Int, val icon: ImageVector, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionsSheet(
    track: Track,
    onDismiss: () -> Unit,
    extraActions: List<SheetAction> = emptyList(),
    viewModel: TrackActionsViewModel = hiltViewModel(),
) {
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val playlists by viewModel.playlistList.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showPlaylistPicker by remember { mutableStateOf(false) }
    val isFavorite = track.id in favorites

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkThumb(model = track.albumArtUri, size = 48.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        track.artist.ifBlank { stringResource(R.string.unknown_artist) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            SheetRow(Icons.Rounded.SkipNext, stringResource(R.string.action_play_next)) {
                viewModel.playNext(track); onDismiss()
            }
            SheetRow(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.action_add_to_queue)) {
                viewModel.addToQueue(track); onDismiss()
            }
            SheetRow(
                if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                stringResource(if (isFavorite) R.string.action_remove_favorite else R.string.action_add_favorite),
            ) {
                viewModel.toggleFavorite(track); onDismiss()
            }
            SheetRow(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.action_add_to_playlist)) {
                showPlaylistPicker = true
            }
            extraActions.forEach { action ->
                SheetRow(action.icon, stringResource(action.labelRes)) { action.onClick(); onDismiss() }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showPlaylistPicker) {
        PlaylistPickerDialog(
            playlists = playlists,
            onPick = { id -> viewModel.addToPlaylist(id, track); showPlaylistPicker = false; onDismiss() },
            onCreate = { name -> viewModel.createPlaylistWith(name, track); showPlaylistPicker = false; onDismiss() },
            onDismiss = { showPlaylistPicker = false },
        )
    }
}

@Composable
private fun SheetRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(18.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Elegir una lista existente o crear una nueva con un nombre. */
@Composable
fun PlaylistPickerDialog(
    playlists: List<Playlist>,
    onPick: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(playlists.isEmpty()) }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (creating) R.string.playlist_new else R.string.action_add_to_playlist)) },
        text = {
            if (creating) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.playlist_name_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Column {
                    playlists.forEach { playlist ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(playlist.id) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(14.dp))
                            Text(playlist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { creating = true }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Text(stringResource(R.string.playlist_new), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(onClick = { if (name.isNotBlank()) onCreate(name) }, enabled = name.isNotBlank()) {
                    Text(stringResource(R.string.action_create))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Diálogo de nombre (crear o renombrar lista). */
@Composable
fun PlaylistNameDialog(
    titleRes: Int,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.playlist_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
