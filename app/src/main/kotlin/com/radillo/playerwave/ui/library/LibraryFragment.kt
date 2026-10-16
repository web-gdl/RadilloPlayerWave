package com.radillo.playerwave.ui.library

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.radillo.playerwave.R
import com.radillo.playerwave.core.model.Album
import com.radillo.playerwave.core.model.Artist
import com.radillo.playerwave.core.model.Folder
import com.radillo.playerwave.core.model.Playlist
import com.radillo.playerwave.core.model.Track
import com.radillo.playerwave.data.permissions.AudioPermission
import com.radillo.playerwave.databinding.FragmentLibraryBinding
import com.radillo.playerwave.ui.common.Row
import com.radillo.playerwave.ui.common.RowAdapter
import com.radillo.playerwave.ui.common.collectWhenStarted
import com.radillo.playerwave.ui.common.setupList
import com.radillo.playerwave.ui.common.showTextInputDialog
import com.radillo.playerwave.ui.common.toRows
import com.radillo.playerwave.ui.common.visibleIf
import com.radillo.playerwave.ui.navigator
import com.radillo.playerwave.ui.settings.SettingsFragment
import dagger.hilt.android.AndroidEntryPoint

/**
 * Biblioteca: pestañas Canciones, Álbumes, Artistas, Carpetas y Listas sobre una única
 * `RecyclerView`. Gestiona también el permiso de lectura de audio.
 */
@AndroidEntryPoint
class LibraryFragment : Fragment(R.layout.fragment_library) {

    private val viewModel: LibraryViewModel by viewModels()
    private val actions: TrackActionsViewModel by activityViewModels()
    private var binding: FragmentLibraryBinding? = null
    private lateinit var adapter: RowAdapter

    private var tracks: List<Track> = emptyList()
    private var albums: List<Album> = emptyList()
    private var artists: List<Artist> = emptyList()
    private var folders: List<Folder> = emptyList()
    private var playlists: List<Playlist> = emptyList()
    private var favoriteCount = 0
    private var nowPlayingId: Long? = null
    private var scanning = false
    private var denials = 0

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) denials++
        refreshPermission()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentLibraryBinding.bind(view)
        binding = b
        adapter = RowAdapter(onClick = ::onRowClick, onMore = ::onRowMore)
        b.list.setupList(adapter)

        b.btnSearch.setOnClickListener { navigator.push(SearchFragment()) }
        b.btnRescan.setOnClickListener { viewModel.rescan() }
        b.btnSettings.setOnClickListener { navigator.push(SettingsFragment()) }
        b.btnPlayAll.setOnClickListener { viewModel.playAll(tracks, 0) }
        b.btnShuffle.setOnClickListener { viewModel.shuffleAll(tracks) }
        b.btnGrant.setOnClickListener { permissionLauncher.launch(AudioPermission.permission) }
        b.btnOpenSettings.setOnClickListener { openAppSettings() }

        val tabViews = mapOf(
            LibraryTab.SONGS to b.tabSongs,
            LibraryTab.ALBUMS to b.tabAlbums,
            LibraryTab.ARTISTS to b.tabArtists,
            LibraryTab.FOLDERS to b.tabFolders,
            LibraryTab.PLAYLISTS to b.tabPlaylists,
        )
        tabViews.forEach { (tab, v) -> v.setOnClickListener { viewModel.selectedTab.value = tab } }

        collectWhenStarted(viewModel.selectedTab) { tab ->
            tabViews.forEach { (t, v) -> v.isSelected = t == tab }
            b.list.scrollToPosition(0)
            render()
        }
        collectWhenStarted(viewModel.tracks) { tracks = it; render() }
        collectWhenStarted(viewModel.albums) { albums = it; render() }
        collectWhenStarted(viewModel.artists) { artists = it; render() }
        collectWhenStarted(viewModel.folders) { folders = it; render() }
        collectWhenStarted(viewModel.playlists) { playlists = it; render() }
        collectWhenStarted(viewModel.favoriteCount) { favoriteCount = it; render() }
        collectWhenStarted(viewModel.nowPlayingId) { nowPlayingId = it; render() }
        collectWhenStarted(viewModel.isScanning) { s ->
            scanning = s
            b.scanning.visibleIf(s)
            b.btnRescan.isEnabled = !s
            render()
        }
    }

    override fun onResume() {
        super.onResume()
        // Vuelve a comprobar al regresar de los ajustes del sistema.
        refreshPermission()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun refreshPermission() {
        val b = binding ?: return
        val granted = AudioPermission.isGranted(requireContext())
        b.permissionPanel.visibleIf(!granted)
        b.btnOpenSettings.visibleIf(denials >= 2)
        if (granted) viewModel.ensureScanned()
    }

    private fun render() {
        val b = binding ?: return
        val ctx = requireContext()
        val tab = viewModel.selectedTab.value
        b.actions.visibleIf(tab == LibraryTab.SONGS && tracks.isNotEmpty())
        val rows: List<Row> = when (tab) {
            LibraryTab.SONGS -> tracks.toRows(ctx, nowPlayingId)
            LibraryTab.ALBUMS -> albums.map { a ->
                Row(
                    key = "a${a.id}",
                    title = a.title.ifBlank { ctx.getString(R.string.unknown_album) },
                    subtitle = listOf(a.artist, ctx.resources.getQuantityString(R.plurals.track_count, a.trackCount, a.trackCount))
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    artUri = a.albumArtUri,
                    placeholder = R.drawable.tile_album,
                    showMore = false,
                )
            }
            LibraryTab.ARTISTS -> artists.map { a ->
                Row(
                    key = "r${a.id}",
                    title = a.name.ifBlank { ctx.getString(R.string.unknown_artist) },
                    subtitle = ctx.resources.getQuantityString(R.plurals.album_count, a.albumCount, a.albumCount) + " · " +
                        ctx.resources.getQuantityString(R.plurals.track_count, a.trackCount, a.trackCount),
                    artUri = null,
                    placeholder = R.drawable.tile_person,
                    showMore = false,
                )
            }
            LibraryTab.FOLDERS -> folders.map { f ->
                Row(
                    key = "f${f.path}",
                    title = f.name,
                    subtitle = ctx.resources.getQuantityString(R.plurals.track_count, f.trackCount, f.trackCount),
                    artUri = null,
                    placeholder = R.drawable.tile_folder,
                    showMore = false,
                )
            }
            LibraryTab.PLAYLISTS -> buildList {
                add(
                    Row(
                        key = "fav",
                        title = ctx.getString(R.string.favorites_title),
                        subtitle = ctx.resources.getQuantityString(R.plurals.track_count, favoriteCount, favoriteCount),
                        artUri = null,
                        placeholder = R.drawable.tile_favorite,
                        showMore = false,
                    ),
                )
                add(
                    Row(
                        key = "new",
                        title = ctx.getString(R.string.playlist_new),
                        subtitle = ctx.getString(R.string.playlist_new_hint),
                        artUri = null,
                        placeholder = R.drawable.tile_add,
                        showMore = false,
                    ),
                )
                playlists.forEach { p ->
                    add(
                        Row(
                            key = "p${p.id}",
                            title = p.name,
                            subtitle = ctx.resources.getQuantityString(R.plurals.track_count, p.trackCount, p.trackCount),
                            artUri = null,
                            placeholder = R.drawable.tile_queue,
                        ),
                    )
                }
            }
        }
        adapter.submitList(rows)
        val emptyText = when {
            rows.isNotEmpty() -> null
            scanning -> getString(R.string.empty_scanning)
            else -> getString(R.string.empty_no_music)
        }
        b.empty.text = emptyText
        b.empty.visibleIf(emptyText != null)
    }

    private fun onRowClick(position: Int) {
        when (viewModel.selectedTab.value) {
            LibraryTab.SONGS -> viewModel.playAll(tracks, position)
            LibraryTab.ALBUMS -> albums.getOrNull(position)?.let {
                navigator.push(CollectionDetailFragment.newInstance(CollectionKind.ALBUM, it.id.toString(), it.title))
            }
            LibraryTab.ARTISTS -> artists.getOrNull(position)?.let {
                navigator.push(CollectionDetailFragment.newInstance(CollectionKind.ARTIST, it.id.toString(), it.name))
            }
            LibraryTab.FOLDERS -> folders.getOrNull(position)?.let {
                navigator.push(CollectionDetailFragment.newInstance(CollectionKind.FOLDER, it.path, it.name))
            }
            LibraryTab.PLAYLISTS -> when (position) {
                0 -> navigator.push(PlaylistDetailFragment.newInstance(FAVORITES_PLAYLIST_ID))
                1 -> requireContext().showTextInputDialog(R.string.playlist_new, "", R.string.action_create, R.string.playlist_name_hint) {
                    viewModel.createPlaylist(it)
                }
                else -> playlists.getOrNull(position - 2)?.let { navigator.push(PlaylistDetailFragment.newInstance(it.id)) }
            }
        }
    }

    private fun onRowMore(position: Int) {
        when (viewModel.selectedTab.value) {
            LibraryTab.SONGS -> tracks.getOrNull(position)?.let { showTrackActions(it, actions) }
            LibraryTab.PLAYLISTS -> {
                val playlist = playlists.getOrNull(position - 2) ?: return
                val anchor = binding?.list?.findViewHolderForAdapterPosition(position)?.itemView ?: return
                PopupMenu(requireContext(), anchor).apply {
                    menu.add(0, 1, 0, R.string.playlist_rename)
                    menu.add(0, 2, 1, R.string.playlist_delete)
                    setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            1 -> requireContext().showTextInputDialog(
                                R.string.playlist_rename, playlist.name, R.string.action_save, R.string.playlist_name_hint,
                            ) { viewModel.renamePlaylist(playlist.id, it) }
                            2 -> viewModel.deletePlaylist(playlist.id)
                        }
                        true
                    }
                    show()
                }
            }
            else -> Unit
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", requireContext().packageName, null))
        startActivity(intent)
    }
}
