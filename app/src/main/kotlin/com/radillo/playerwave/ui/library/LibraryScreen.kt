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
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import com.radillo.playerwave.core.model.Playlist
import com.radillo.playerwave.core.model.Track
import com.radillo.playerwave.core.ui.components.ArtworkImage
import com.radillo.playerwave.core.ui.components.TrackRow

enum class LibraryTab(val labelRes: Int) {
    SONGS(R.string.tab_songs),
    ALBUMS(R.string.tab_albums),
    ARTISTS(R.string.tab_artists),
    FOLDERS(R.string.tab_folders),
    PLAYLISTS(R.string.tab_playlists),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenAlbum: (Album) -> Unit,
    onOpenArtist: (Artist) -> Unit,
    onOpenFolder: (Folder) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val favoriteCount by viewModel.favoriteCount.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val nowPlayingId by viewModel.nowPlayingId.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menuTrack by remember { mutableStateOf<Track?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_library)) },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.action_search))
                    }
                    IconButton(onClick = viewModel::rescan, enabled = !isScanning) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_rescan))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
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
                    onMenu = { menuTrack = it },
                    isScanning = isScanning,
                )
                LibraryTab.ALBUMS -> AlbumGrid(albums, onOpenAlbum)
                LibraryTab.ARTISTS -> ArtistList(artists, onOpenArtist)
                LibraryTab.FOLDERS -> FolderList(folders, onOpenFolder)
                LibraryTab.PLAYLISTS -> PlaylistsTab(
                    playlists = playlists,
                    favoriteCount = favoriteCount,
                    onOpen = onOpenPlaylist,
                    onCreate = viewModel::createPlaylist,
                    onRename = viewModel::renamePlaylist,
                    onDelete = viewModel::deletePlaylist,
                )
            }
        }
    }

    menuTrack?.let { track ->
        TrackActionsSheet(track = track, onDismiss = { menuTrack = null })
    }
}

/**
 * Lista de canciones con cabecera de acciones. `onMenu` abre la hoja de acciones de la pista.
 */
@Composable
fun SongsList(
    tracks: List<Track>,
    nowPlayingId: Long?,
    onPlay: (Int) -> Unit,
    onShuffle: () -> Unit,
    onMenu: (Track) -> Unit,
    isScanning: Boolean = false,
    emptyText: String = stringResource(if (isScanning) R.string.empty_scanning else R.string.empty_no_music),
    header: (@Composable () -> Unit)? = null,
) {
    if (tracks.isEmpty()) {
        EmptyState(text = emptyText)
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
            TrackRow(
                track = track,
                highlighted = track.id == nowPlayingId,
                onClick = { onPlay(index) },
                onMore = { onMenu(track) },
            )
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
private fun PlaylistsTab(
    playlists: List<Playlist>,
    favoriteCount: Int,
    onOpen: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Playlist?>(null) }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            IconRow(
                icon = { Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.secondary) },
                title = stringResource(R.string.favorites_title),
                subtitle = pluralStringResource(R.plurals.track_count, favoriteCount, favoriteCount),
                onClick = { onOpen(FAVORITES_PLAYLIST_ID) },
            )
        }
        item {
            IconRow(
                icon = { Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary) },
                title = stringResource(R.string.playlist_new),
                subtitle = stringResource(R.string.playlist_new_hint),
                onClick = { creating = true },
            )
        }
        items(playlists, key = { it.id }) { playlist ->
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                IconRow(
                    icon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.primary) },
                    title = playlist.name,
                    subtitle = pluralStringResource(R.plurals.track_count, playlist.trackCount, playlist.trackCount),
                    onClick = { onOpen(playlist.id) },
                    trailing = {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = null)
                        }
                    },
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.playlist_rename)) },
                        onClick = { menuOpen = false; renaming = playlist },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.playlist_delete)) },
                        onClick = { menuOpen = false; onDelete(playlist.id) },
                    )
                }
            }
        }
    }

    if (creating) {
        PlaylistNameDialog(
            titleRes = R.string.playlist_new,
            initial = "",
            onConfirm = { onCreate(it); creating = false },
            onDismiss = { creating = false },
        )
    }
    renaming?.let { playlist ->
        PlaylistNameDialog(
            titleRes = R.string.playlist_rename,
            initial = playlist.name,
            onConfirm = { onRename(playlist.id, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun IconRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = if (trailing == null) 20.dp else 4.dp, top = 14.dp, bottom = 14.dp),
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
        trailing?.invoke()
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

/** Id reservado para la lista virtual de favoritos. */
const val FAVORITES_PLAYLIST_ID = -1L
