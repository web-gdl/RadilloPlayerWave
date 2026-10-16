package com.radillo.playerwave.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radillo.playerwave.R
import com.radillo.playerwave.core.model.Album
import com.radillo.playerwave.core.model.Artist
import com.radillo.playerwave.core.model.Folder
import com.radillo.playerwave.core.model.Track
import com.radillo.playerwave.core.ui.components.ArtworkImage
import com.radillo.playerwave.core.ui.components.TrackRow

enum class LibraryTab(val labelRes: Int) {
    SONGS(R.string.tab_songs),
    ALBUMS(R.string.tab_albums),
    ARTISTS(R.string.tab_artists),
    FOLDERS(R.string.tab_folders),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenAlbum: (Album) -> Unit,
    onOpenArtist: (Artist) -> Unit,
    onOpenFolder: (Folder) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val nowPlayingId by viewModel.nowPlayingId.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_library)) },
                actions = {
                    IconButton(onClick = viewModel::rescan, enabled = !isScanning) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_rescan))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                LibraryTab.entries.forEachIndexed { index, item ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(stringResource(item.labelRes)) },
                    )
                }
            }
            if (isScanning) LinearProgressIndicator(Modifier.fillMaxWidth())

            when (LibraryTab.entries[tab]) {
                LibraryTab.SONGS -> SongsList(
                    tracks = tracks,
                    nowPlayingId = nowPlayingId,
                    onPlay = { index -> viewModel.playAll(tracks, index) },
                    onShuffle = { viewModel.shuffleAll(tracks) },
                    onPlayNext = viewModel::playNext,
                    onAddToQueue = viewModel::addToQueue,
                    isScanning = isScanning,
                )
                LibraryTab.ALBUMS -> AlbumGrid(albums, onOpenAlbum)
                LibraryTab.ARTISTS -> ArtistList(artists, onOpenArtist)
                LibraryTab.FOLDERS -> FolderList(folders, onOpenFolder)
            }
        }
    }
}

@Composable
fun SongsList(
    tracks: List<Track>,
    nowPlayingId: Long?,
    onPlay: (Int) -> Unit,
    onShuffle: () -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    isScanning: Boolean = false,
    header: (@Composable () -> Unit)? = null,
) {
    if (tracks.isEmpty()) {
        EmptyState(
            text = stringResource(if (isScanning) R.string.empty_scanning else R.string.empty_no_music),
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        if (header != null) item { header() }
        item {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(onClick = { onPlay(0) }) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_play_all))
                }
                OutlinedButton(onClick = onShuffle) {
                    Icon(Icons.Rounded.Shuffle, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_shuffle))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = pluralStringResource(R.plurals.track_count, tracks.size, tracks.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
        items(tracks.size, key = { tracks[it].id }) { index ->
            val track = tracks[index]
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                TrackRow(
                    track = track,
                    highlighted = track.id == nowPlayingId,
                    onClick = { onPlay(index) },
                    onMore = { menuOpen = true },
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_play_next)) },
                        onClick = { menuOpen = false; onPlayNext(track) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_add_to_queue)) },
                        onClick = { menuOpen = false; onAddToQueue(track) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumGrid(albums: List<Album>, onOpen: (Album) -> Unit) {
    if (albums.isEmpty()) { EmptyState(stringResource(R.string.empty_no_music)); return }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(albums, key = { it.id }) { album ->
            Column(Modifier.clickable { onOpen(album) }) {
                ArtworkImage(model = album.albumArtUri, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                Spacer(Modifier.height(8.dp))
                Text(
                    album.title.ifBlank { stringResource(R.string.unknown_album) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    album.artist.ifBlank { stringResource(R.string.unknown_artist) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ArtistList(artists: List<Artist>, onOpen: (Artist) -> Unit) {
    if (artists.isEmpty()) { EmptyState(stringResource(R.string.empty_no_music)); return }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        items(artists, key = { it.id }) { artist ->
            IconRow(
                icon = { Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.primary) },
                title = artist.name.ifBlank { stringResource(R.string.unknown_artist) },
                subtitle = pluralStringResource(R.plurals.album_count, artist.albumCount, artist.albumCount) +
                    " · " + pluralStringResource(R.plurals.track_count, artist.trackCount, artist.trackCount),
                onClick = { onOpen(artist) },
            )
        }
    }
}

@Composable
private fun FolderList(folders: List<Folder>, onOpen: (Folder) -> Unit) {
    if (folders.isEmpty()) { EmptyState(stringResource(R.string.empty_no_music)); return }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        items(folders, key = { it.path }) { folder ->
            IconRow(
                icon = { Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.tertiary) },
                title = folder.name.ifBlank { folder.path },
                subtitle = folder.path,
                onClick = { onOpen(folder) },
            )
        }
    }
}

@Composable
private fun IconRow(icon: @Composable () -> Unit, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
