package com.radillo.playerwave.ui.visualizer

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.radillo.playerwave.R
import com.radillo.playerwave.databinding.FragmentVisualizerBinding
import com.radillo.playerwave.ui.ChromeMode
import com.radillo.playerwave.ui.common.collectWhenStarted
import com.radillo.playerwave.ui.common.visibleIf
import com.radillo.playerwave.ui.navigator
import com.radillo.playerwave.visualizer.engine.DataState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine

/**
 * Visualizador. La imagen la dibuja `VisualizerSurfaceView` en su propio hilo; aquí solo hay
 * controles planos encima. En modo inmersivo oculta la navegación y las barras del sistema, y los
 * controles se esconden solos a los 4 s (un toque los muestra u oculta).
 */
@AndroidEntryPoint
class VisualizerFragment : Fragment(R.layout.fragment_visualizer), ChromeMode {

    private val viewModel: VisualizerViewModel by viewModels()
    private var binding: FragmentVisualizerBinding? = null
    private val handler = Handler(Looper.getMainLooper())
    private val hideControls = Runnable { setControlsVisible(false) }
    private val chips = mutableMapOf<String, TextView>()

    private val immersive: Boolean get() = arguments?.getBoolean(ARG_IMMERSIVE) == true
    override val hidesNavigation: Boolean get() = immersive
    override val hidesMiniPlayer: Boolean get() = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentVisualizerBinding.bind(view)
        binding = b
        b.surface.setEngine(viewModel.engine)
        b.surface.setOnClickListener { setControlsVisible(b.overlayTop.visibility != View.VISIBLE) }
        b.btnTune.setOnClickListener { VisualizerSettingsDialog.show(this, viewModel) }
        b.btnFullscreen.setImageResource(if (immersive) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen)
        b.btnFullscreen.contentDescription = getString(if (immersive) R.string.vis_exit_fullscreen else R.string.vis_fullscreen)
        b.btnFullscreen.setOnClickListener {
            if (immersive) navigator.back() else navigator.push(newInstance(immersive = true))
        }
        b.btnPlay.setOnClickListener { viewModel.togglePlayPause() }
        b.btnNext.setOnClickListener { viewModel.next() }
        b.btnPrevious.setOnClickListener { viewModel.previous() }

        val inflater = LayoutInflater.from(requireContext())
        viewModel.sceneIds.forEach { id ->
            val chip = inflater.inflate(R.layout.view_chip, b.sceneChips, false) as TextView
            chip.setText(sceneLabelRes(id))
            chip.setOnClickListener { viewModel.selectScene(id) }
            b.sceneChips.addView(chip)
            chips[id] = chip
        }

        collectWhenStarted(viewModel.settings) { s ->
            chips.forEach { (id, chip) -> chip.isSelected = id == s.sceneId }
            b.root.isKeepScreenOn = s.keepScreenOn
        }
        collectWhenStarted(combine(viewModel.playbackState, viewModel.dataState) { p, d -> p to d }) { (p, d) ->
            val track = p.currentTrack
            b.trackTitle.text = track?.title.orEmpty()
            b.trackSubtitle.text = track?.artist.orEmpty()
            b.trackTitle.visibleIf(track != null)
            b.trackSubtitle.visibleIf(track != null)
            val status = when {
                track == null -> R.string.vis_idle
                d == DataState.SYNTHETIC && p.isPlaying -> R.string.vis_no_audio_data
                else -> null
            }
            b.status.visibleIf(status != null)
            if (status != null) b.status.setText(status)
            b.btnPlay.setImageResource(if (p.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
            b.btnPlay.contentDescription = getString(if (p.isPlaying) R.string.action_pause else R.string.action_play)
        }
    }

    override fun onResume() {
        super.onResume()
        if (immersive) {
            val window = requireActivity().window
            WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
            scheduleHide()
        }
    }

    override fun onPause() {
        handler.removeCallbacks(hideControls)
        if (immersive) {
            val window = requireActivity().window
            WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        }
        super.onPause()
    }

    override fun onDestroyView() {
        chips.clear()
        binding = null
        super.onDestroyView()
    }

    private fun setControlsVisible(visible: Boolean) {
        val b = binding ?: return
        b.overlayTop.visibleIf(visible)
        b.overlayBottom.visibleIf(visible)
        if (visible) scheduleHide()
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideControls)
        if (immersive) handler.postDelayed(hideControls, 4_000)
    }

    companion object {
        private const val ARG_IMMERSIVE = "immersive"

        fun newInstance(immersive: Boolean = false) = VisualizerFragment().apply {
            arguments = bundleOf(ARG_IMMERSIVE to immersive)
        }
    }
}
