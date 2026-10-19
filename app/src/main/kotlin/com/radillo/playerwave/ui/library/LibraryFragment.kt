package com.radillo.playerwave.ui.library

import android.app.AlertDialog
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
import androidx.recyclerview.widget.LinearLayoutManager
import com.radillo.playerwave.R
import com.radillo.playerwave.data.library.ScanResult
import com.radillo.playerwave.data.permissions.AudioPermission
import com.radillo.playerwave.databinding.FragmentLibraryBinding
import com.radillo.playerwave.ui.common.RowAdapter
import com.radillo.playerwave.ui.common.collectWhenStarted
import com.radillo.playerwave.ui.common.setupList
import com.radillo.playerwave.ui.common.showTextInputDialog
import com.radillo.playerwave.ui.common.visibleIf
import com.radillo.playerwave.ui.navigator
import com.radillo.playerwave.ui.settings.SettingsFragment
import dagger.hilt.android.AndroidEntryPoint

/**
 * Biblioteca: pestañas Canciones, Álbumes, Artistas, Carpetas y Listas sobre una única
 * `RecyclerView`. Recuerda la pestaña y la posición de cada lista entre sesiones, y gestiona el
 * permiso de lectura de audio.
 */
@AndroidEntryPoint
class LibraryFragment : Fragment(R.layout.fragment_library) {

    private val viewModel: LibraryViewModel by viewModels()
    private val actions: TrackActionsViewModel by activityViewModels()
    private var binding: FragmentLibraryBinding? = null
    private lateinit var adapter: RowAdapter
    private var content: LibraryContent? = null
    private var scanning = false
    private var denials = 0
    /** Pestaña cuya posición guardada ya se aplicó (se restaura una vez por pestaña). */
    private var restoredScrollFor: LibraryTab? = null

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
        b.btnEmptyAction.setOnClickListener { viewModel.rescan() }
        b.btnSettings.setOnClickListener { navigator.push(SettingsFragment()) }
        // En pantallas anchas Buscar y Ajustes están en la barra superior global: aquí sobrarían.
        val globalActions = resources.getBoolean(R.bool.top_bar_actions)
        b.btnSearch.visibleIf(!globalActions)
        b.btnSettings.visibleIf(!globalActions)
        b.btnPlayAll.setOnClickListener { content?.let { viewModel.playAll(it.tracks, 0) } }
        b.btnShuffle.setOnClickListener { content?.let { viewModel.shuffleAll(it.tracks) } }
        b.btnGrant.setOnClickListener { permissionLauncher.launch(AudioPermission.permission) }
        b.btnOpenSettings.setOnClickListener { openAppSettings() }

        val tabViews = mapOf(
            LibraryTab.SONGS to b.tabSongs,
            LibraryTab.ALBUMS to b.tabAlbums,
            LibraryTab.ARTISTS to b.tabArtists,
            LibraryTab.FOLDERS to b.tabFolders,
            LibraryTab.PLAYLISTS to b.tabPlaylists,
        )
        tabViews.forEach { (tab, v) ->
            v.setOnClickListener {
                if (viewModel.selectedTab.value == tab) {
                    b.list.scrollToPosition(0) // tocar la pestaña activa vuelve arriba
                } else {
                    saveScroll()
                    viewModel.selectTab(tab)
                }
            }
        }

        collectWhenStarted(viewModel.selectedTab) { tab ->
            tabViews.forEach { (t, v) -> v.isSelected = t == tab }
            tabViews[tab]?.let { selected -> b.tabs.post { (b.tabs.parent as? android.widget.HorizontalScrollView)?.smoothScrollTo(selected.left - b.tabs.paddingStart, 0) } }
        }
        collectWhenStarted(viewModel.content) { c -> if (c != null) render(c) }
        collectWhenStarted(viewModel.isScanning) { s ->
            scanning = s
            b.scanning.visibleIf(s)
            b.btnRescan.isEnabled = !s
            b.btnEmptyAction.isEnabled = !s
            content?.let(::render)
        }
        collectWhenStarted(viewModel.scanResults, ::announce)
    }

    override fun onResume() {
        super.onResume()
        // De vuelta en la raíz de la biblioteca: ya no hay carpeta abierta que recordar.
        LastCollection.clear(requireContext())
        // Vuelve a comprobar al regresar de los ajustes del sistema.
        refreshPermission()
    }

    override fun onPause() {
        saveScroll()
        super.onPause()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun saveScroll() {
        val lm = binding?.list?.layoutManager as? LinearLayoutManager ?: return
        val tab = content?.tab ?: return
        viewModel.saveScroll(tab, lm.findFirstVisibleItemPosition())
    }

    private fun refreshPermission() {
        val b = binding ?: return
        val granted = AudioPermission.isGranted(requireContext())
        b.permissionPanel.visibleIf(!granted)
        b.btnOpenSettings.visibleIf(denials >= 2)
        if (granted) viewModel.ensureScanned()
    }

    private fun render(c: LibraryContent) {
        val b = binding ?: return
        val tabChanged = content?.tab != c.tab
        content = c
        b.actions.visibleIf(c.tab == LibraryTab.SONGS && c.tracks.isNotEmpty())
        b.libraryCount.text = if (c.songCount > 0) resources.getQuantityString(R.plurals.track_count, c.songCount, c.songCount) else ""
        adapter.submitList(c.rows) {
            if (restoredScrollFor != c.tab && c.rows.isNotEmpty()) {
                restoredScrollFor = c.tab
                (b.list.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                    viewModel.savedScroll(c.tab).coerceAtMost(c.rows.lastIndex), 0,
                )
            } else if (tabChanged) {
                b.list.scrollToPosition(0)
            }
        }
        val emptyText = when {
            c.rows.isNotEmpty() -> null
            scanning -> getString(R.string.empty_scanning)
            else -> getString(R.string.empty_no_music)
        }
        b.empty.text = emptyText
        b.emptyPanel.visibleIf(emptyText != null)
        b.btnEmptyAction.visibleIf(!scanning)
    }

    /** Resultado visible de «Buscar música nueva» (sin él, no se sabe si el botón hizo algo). */
    private fun announce(result: ScanResult) {
        val ctx = context ?: return
        val parts = buildList {
            if (result.added > 0) add(resources.getQuantityString(R.plurals.scan_added, result.added, result.added))
            if (result.removed > 0) add(resources.getQuantityString(R.plurals.scan_removed, result.removed, result.removed))
        }
        ctx.toast(if (parts.isEmpty()) getString(R.string.scan_none) else parts.joinToString(" · "))
    }

    private fun onRowClick(position: Int) {
        val c = content ?: return
        when (c.tab) {
            LibraryTab.SONGS -> viewModel.playAll(c.tracks, position)
            LibraryTab.ALBUMS -> c.albums.getOrNull(position)?.let {
                navigator.push(CollectionDetailFragment.newInstance(CollectionKind.ALBUM, it.id.toString(), it.title))
            }
            LibraryTab.ARTISTS -> c.artists.getOrNull(position)?.let {
                navigator.push(CollectionDetailFragment.newInstance(CollectionKind.ARTIST, it.id.toString(), it.name))
            }
            LibraryTab.FOLDERS -> c.folders.getOrNull(position)?.let {
                navigator.push(CollectionDetailFragment.newInstance(CollectionKind.FOLDER, it.path, it.name))
            }
            LibraryTab.PLAYLISTS -> when (position) {
                0 -> navigator.push(PlaylistDetailFragment.newInstance(FAVORITES_PLAYLIST_ID))
                1 -> requireContext().showTextInputDialog(R.string.playlist_new, "", R.string.action_create, R.string.playlist_name_hint) {
                    viewModel.createPlaylist(it)
                }
                else -> c.playlists.getOrNull(position - 2)?.let { navigator.push(PlaylistDetailFragment.newInstance(it.id)) }
            }
        }
    }

    private fun onRowMore(position: Int) {
        val c = content ?: return
        when (c.tab) {
            LibraryTab.SONGS -> c.tracks.getOrNull(position)?.let { showTrackActions(it, actions) }
            LibraryTab.PLAYLISTS -> {
                val playlist = c.playlists.getOrNull(position - 2) ?: return
                val anchor = binding?.list?.findViewHolderForAdapterPosition(position)?.itemView ?: return
                PopupMenu(requireContext(), anchor).apply {
                    menu.add(0, 1, 0, R.string.playlist_rename)
                    menu.add(0, 2, 1, R.string.playlist_delete)
                    setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            1 -> requireContext().showTextInputDialog(
                                R.string.playlist_rename, playlist.name, R.string.action_save, R.string.playlist_name_hint,
                            ) { viewModel.renamePlaylist(playlist.id, it) }
                            // Prevención de errores: borrar una lista no se puede deshacer.
                            2 -> AlertDialog.Builder(requireContext())
                                .setMessage(getString(R.string.playlist_delete_confirm, playlist.name))
                                .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.deletePlaylist(playlist.id) }
                                .setNegativeButton(R.string.action_cancel, null)
                                .show()
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
