package com.radillo.playerwave.ui.library

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
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

/** Búsqueda por título, artista o álbum (con espera de 250 ms entre pulsaciones). */
@AndroidEntryPoint
class SearchFragment : Fragment(R.layout.fragment_track_list) {

    private val viewModel: SearchViewModel by viewModels()
    private val actions: TrackActionsViewModel by activityViewModels()
    private var tracks: List<Track> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentTrackListBinding.bind(view)
        val adapter = RowAdapter(
            onClick = { viewModel.play(it) },
            onMore = { pos -> tracks.getOrNull(pos)?.let { showTrackActions(it, actions) } },
        )
        b.list.setupList(adapter)
        b.title.visibleIf(false)
        b.actions.visibleIf(false)
        b.searchField.visibleIf(true)
        b.btnBack.setOnClickListener {
            hideKeyboard(b.searchField)
            navigator.back()
        }
        b.btnClear.setOnClickListener { b.searchField.setText("") }
        b.searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString().orEmpty()
                viewModel.setQuery(q)
                b.btnClear.visibleIf(q.isNotEmpty())
            }
        })
        if (savedInstanceState == null) {
            b.searchField.requestFocus()
            b.searchField.post {
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(b.searchField, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        collectWhenStarted(combine(viewModel.results, viewModel.nowPlayingId) { r, n -> r to n }) { (list, nowPlaying) ->
            tracks = list
            adapter.submitList(list.toRows(requireContext(), nowPlaying))
            val query = viewModel.query.value
            b.empty.setText(if (query.isBlank()) R.string.search_prompt else R.string.search_no_results)
            b.empty.visibleIf(list.isEmpty())
        }
    }

    private fun hideKeyboard(v: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(v.windowToken, 0)
    }
}
