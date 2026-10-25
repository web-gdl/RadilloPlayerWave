package com.radillo.playerwave.ui.player

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.radillo.playerwave.R
import com.radillo.playerwave.databinding.FragmentTrackListBinding
import com.radillo.playerwave.ui.ChromeMode
import com.radillo.playerwave.ui.common.RowAdapter
import com.radillo.playerwave.ui.common.collectWhenStarted
import com.radillo.playerwave.ui.common.setupList
import com.radillo.playerwave.ui.common.toRow
import com.radillo.playerwave.ui.common.visibleIf
import com.radillo.playerwave.ui.navigator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Cola de reproducción: tocar salta a la canción; la X la quita de la cola. */
@AndroidEntryPoint
class QueueFragment : Fragment(R.layout.fragment_track_list), ChromeMode {

    override val hidesMiniPlayer: Boolean get() = true

    private val viewModel: PlayerViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentTrackListBinding.bind(view)
        val adapter = RowAdapter(
            onClick = { viewModel.skipToQueueItem(it) },
            onMore = { viewModel.removeFromQueue(it) },
            moreIcon = R.drawable.ic_close,
        )
        b.list.setupList(adapter)
        b.actions.visibleIf(false)
        b.btnBack.setOnClickListener { navigator.back() }
        b.empty.setText(R.string.queue_empty)
        b.title.setText(R.string.queue_title)
        var scrolled = false
        // Solo cuando cambia la cola o la canción actual: el estado también cambia con cada tic de
        // progreso y rehacer miles de filas dos veces por segundo se nota en un A7.
        val queueState = viewModel.state.map { it.queue to it.queueIndex }.distinctUntilChanged()
        collectWhenStarted(queueState) { (queue, queueIndex) ->
            val ctx = requireContext()
            b.listCount.text = resources.getQuantityString(R.plurals.track_count, queue.size, queue.size)
            adapter.submitList(
                queue.mapIndexed { index, track ->
                    track.toRow(ctx, if (index == queueIndex) track.id else null, key = "$index-${track.id}")
                },
            )
            b.empty.visibleIf(queue.isEmpty())
            if (!scrolled && queueIndex > 0) {
                scrolled = true
                b.list.scrollToPosition(queueIndex)
            }
        }
    }
}
