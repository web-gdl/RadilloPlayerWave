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

/** Canciones de un álbum, artista o carpeta. */
@AndroidEntryPoint
class CollectionDetailFragment : Fragment(R.layout.fragment_track_list) {

    private val viewModel: CollectionDetailViewModel by viewModels()
    private val actions: TrackActionsViewModel by activityViewModels()
    private var tracks: List<Track> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentTrackListBinding.bind(view)
        val adapter = RowAdapter(
            onClick = { viewModel.play(it) },
            onMore = { pos -> tracks.getOrNull(pos)?.let { showTrackActions(it, actions) } },
        )
        b.list.setupList(adapter)
        b.title.text = viewModel.title
        b.btnBack.setOnClickListener { navigator.back() }
        b.btnPlayAll.setOnClickListener { viewModel.play(0) }
        b.btnShuffle.setOnClickListener { viewModel.shuffle() }
        collectWhenStarted(combine(viewModel.tracks, viewModel.nowPlayingId) { t, n -> t to n }) { (list, nowPlaying) ->
            tracks = list
            adapter.submitList(list.toRows(requireContext(), nowPlaying))
            b.actions.visibleIf(list.isNotEmpty())
            b.empty.setText(R.string.empty_scanning)
            b.empty.visibleIf(list.isEmpty())
        }
    }

    companion object {
        fun newInstance(kind: CollectionKind, key: String, title: String) = CollectionDetailFragment().apply {
            arguments = bundleOf(
                CollectionDetailViewModel.ARG_KIND to kind.name,
                CollectionDetailViewModel.ARG_KEY to key,
                CollectionDetailViewModel.ARG_TITLE to title,
            )
        }
    }
}
