package com.radillo.playerwave.ui.visualizer

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.abs

/**
 * Visualizador a pantalla completa (se abre con tres toques sobre la carátula de «Reproduciendo»).
 * Solo la imagen, sin controles: **un toque** vuelve al reproductor y **deslizar** a los lados cambia
 * de visualizador. Oculta la navegación de la app y las barras del sistema.
 */
@AndroidEntryPoint
class VisualizerFragment : Fragment(R.layout.fragment_visualizer), ChromeMode {

    private val viewModel: VisualizerViewModel by viewModels()
    private var binding: FragmentVisualizerBinding? = null
    private val handler = Handler(Looper.getMainLooper())
    private val hideLabel = Runnable { binding?.sceneLabel?.visibleIf(false) }

    override val hidesNavigation: Boolean get() = true
    override val hidesMiniPlayer: Boolean get() = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentVisualizerBinding.bind(view)
        binding = b
        b.surface.setEngine(viewModel.engine)
        // Lectores de pantalla y teclado: «activar» vuelve al reproductor.
        b.root.setOnClickListener { navigator.back() }
        setUpGestures(b)

        collectWhenStarted(viewModel.settings.map { it.keepScreenOn }.distinctUntilChanged()) { b.root.keepScreenOn = it }
        // Solo si hay canción y si suena: el estado completo cambia con cada tic de progreso.
        val playback = viewModel.playbackState.map { (it.currentTrack != null) to it.isPlaying }.distinctUntilChanged()
        collectWhenStarted(combine(playback, viewModel.dataState) { p, d -> p to d }) { (p, d) ->
            val (hasTrack, isPlaying) = p
            val status = when {
                !hasTrack -> R.string.vis_idle
                d == DataState.SYNTHETIC && isPlaying -> R.string.vis_no_audio_data
                else -> null
            }
            b.status.visibleIf(status != null)
            if (status != null) b.status.setText(status)
        }
        if (savedInstanceState == null) showLabel(getString(R.string.vis_fullscreen_hint), HINT_MS)
    }

    @SuppressLint("ClickableViewAccessibility") // la activación accesible va por el OnClickListener
    private fun setUpGestures(b: FragmentVisualizerBinding) {
        val slop = ViewConfiguration.get(requireContext()).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        b.root.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    when {
                        abs(dx) <= slop && abs(dy) <= slop -> navigator.back()
                        abs(dx) >= view.width * SWIPE_FRACTION && abs(dx) >= abs(dy) * 1.5f -> stepScene(if (dx < 0) 1 else -1)
                    }
                }
            }
            true
        }
    }

    override fun onResume() {
        super.onResume()
        val window = requireActivity().window
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onPause() {
        val window = requireActivity().window
        WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        super.onPause()
    }

    override fun onDestroyView() {
        handler.removeCallbacks(hideLabel)
        binding = null
        super.onDestroyView()
    }

    /** Visualizador anterior o siguiente, en bucle; su nombre se muestra un momento. */
    private fun stepScene(delta: Int) {
        val ids = viewModel.sceneIds
        if (ids.isEmpty()) return
        val current = ids.indexOf(viewModel.settings.value.sceneId).coerceAtLeast(0)
        val next = ids[(current + delta).mod(ids.size)]
        viewModel.selectScene(next)
        showLabel(getString(sceneLabelRes(next)), LABEL_MS)
    }

    private fun showLabel(text: String, millis: Long) {
        val b = binding ?: return
        b.sceneLabel.text = text
        b.sceneLabel.visibleIf(true)
        handler.removeCallbacks(hideLabel)
        handler.postDelayed(hideLabel, millis)
    }

    companion object {
        private const val SWIPE_FRACTION = 0.12f
        private const val LABEL_MS = 1_500L
        private const val HINT_MS = 3_500L

        fun newInstance() = VisualizerFragment()
    }
}
