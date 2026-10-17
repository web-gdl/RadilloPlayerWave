package com.radillo.playerwave.ui.library

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.radillo.playerwave.R
import com.radillo.playerwave.core.model.Track
import com.radillo.playerwave.databinding.FragmentTrackListBinding
import com.radillo.playerwave.ui.common.RowAdapter
import com.radillo.playerwave.ui.common.collectWhenStarted
import com.radillo.playerwave.ui.common.setupList
import com.radillo.playerwave.ui.common.toRows
import com.radillo.playerwave.ui.common.visibleIf
import com.radillo.playerwave.ui.navigator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine

/** Favoritos o una lista del usuario, con reordenación y borrado desde el menú de cada canción. */
@AndroidEntryPoint
class PlaylistDetailFragment : Fragment(R.layout.fragment_track_list) {

    private val viewModel: PlaylistDetailViewModel by viewModels()
    private val actions: TrackActionsViewModel by activityViewModels()
    private var tracks: List<Track> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentTrackListBinding.bind(view)
        val adapter = RowAdapter(onClick = { viewModel.play(it) }, onMore = ::showMenu)
        b.list.setupList(adapter)
        b.btnBack.setOnClickListener { navigator.back() }
        b.btnPlayAll.setOnClickListener { viewModel.play(0) }
        b.btnShuffle.setOnClickListener { viewModel.shuffle() }
        if (viewModel.isFavorites) b.title.setText(R.string.favorites_title)
        collectWhenStarted(viewModel.name) { name -> if (!viewModel.isFavorites) b.title.text = name.orEmpty() }
        collectWhenStarted(combine(viewModel.tracks, viewModel.nowPlayingId) { t, n -> t to n }) { (list, nowPlaying) ->
            tracks = list
            adapter.submitList(list.toRows(requireContext(), nowPlaying))
            b.actions.visibleIf(list.isNotEmpty())
            b.listCount.text = resources.getQuantityString(R.plurals.track_count, list.size, list.size)
            b.empty.setText(if (viewModel.isFavorites) R.string.favorites_empty else R.string.playlist_empty)
            b.empty.visibleIf(list.isEmpty())
        }
    }

    private fun showMenu(position: Int) {
        val track = tracks.getOrNull(position) ?: return
        val extras = buildList {
            if (!viewModel.isFavorites) {
                add(ExtraAction(R.string.action_move_up) { viewModel.move(track, -1) })
                add(ExtraAction(R.string.action_move_down) { viewModel.move(track, 1) })
                add(ExtraAction(R.string.action_remove_from_playlist) { viewModel.remove(track) })
            }
        }
        showTrackActions(track, actions, extras)
    }

    companion object {
        fun newInstance(playlistId: Long) = PlaylistDetailFragment().apply {
            // El ViewModel lee el id como texto (igual que la ruta de navegación anterior).
            arguments = bundleOf(PlaylistDetailViewModel.ARG_ID to playlistId.toString())
        }
    }
}
