package com.radillo.playerwave.ui.visualizer

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
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
 * controles planos encima: escena actual con flechas para cambiarla (tocar el nombre abre la lista
 * completa) y los controles de reproducción. Mientras suena, los controles se esconden solos para
 * dejar ver la imagen (un toque los muestra u oculta). En modo inmersivo además se ocultan la
 * navegación y las barras del sistema.
 */
@AndroidEntryPoint
class VisualizerFragment : Fragment(R.layout.fragment_visualizer), ChromeMode {

    private val viewModel: VisualizerViewModel by viewModels()
    private var binding: FragmentVisualizerBinding? = null
    private val handler = Handler(Looper.getMainLooper())
    private val hideControls = Runnable { setControlsVisible(false) }
    private var playing = false

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
        b.btnPlay.setOnClickListener { viewModel.togglePlayPause(); scheduleHide() }
        b.btnNext.setOnClickListener { viewModel.next(); scheduleHide() }
        b.btnPrevious.setOnClickListener { viewModel.previous(); scheduleHide() }
        b.btnScenePrevious.setOnClickListener { stepScene(-1) }
        b.btnSceneNext.setOnClickListener { stepScene(1) }
        b.sceneName.setOnClickListener { chooseScene() }

        collectWhenStarted(viewModel.settings) { s ->
            b.sceneName.setText(sceneLabelRes(s.sceneId))
            b.root.keepScreenOn = s.keepScreenOn
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
            if (p.isPlaying != playing) {
                playing = p.isPlaying
                // En pausa los controles se quedan a la vista; sonando, se esconden solos.
                if (playing) scheduleHide() else setControlsVisible(true)
            }
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
        }
        scheduleHide()
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
        handler.removeCallbacks(hideControls)
        binding = null
        super.onDestroyView()
    }

    /** Escena anterior o siguiente, en bucle: un toque grande en vez de buscar en una lista. */
    private fun stepScene(delta: Int) {
        val ids = viewModel.sceneIds
        if (ids.isEmpty()) return
        val current = ids.indexOf(viewModel.settings.value.sceneId).coerceAtLeast(0)
        viewModel.selectScene(ids[(current + delta).mod(ids.size)])
        scheduleHide()
    }

    /** Lista completa solo cuando se pide (revelación progresiva). */
    private fun chooseScene() {
        val ids = viewModel.sceneIds
        val labels = ids.map { getString(sceneLabelRes(it)) }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.vis_choose_scene)
            .setSingleChoiceItems(labels, ids.indexOf(viewModel.settings.value.sceneId)) { dialog, which ->
                viewModel.selectScene(ids[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_close, null)
            .show()
        handler.removeCallbacks(hideControls)
    }

    private fun setControlsVisible(visible: Boolean) {
        val b = binding ?: return
        b.overlayTop.visibleIf(visible)
        b.overlayBottom.visibleIf(visible)
        if (visible) scheduleHide()
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideControls)
        if (playing || immersive) handler.postDelayed(hideControls, if (immersive) 4_000L else 6_000L)
    }

    companion object {
        private const val ARG_IMMERSIVE = "immersive"

        fun newInstance(immersive: Boolean = false) = VisualizerFragment().apply {
            arguments = bundleOf(ARG_IMMERSIVE to immersive)
        }
    }
}
