package com.radillo.playerwave.ui.player

import com.radillo.playerwave.ui.library.ExtraAction
import android.widget.Toast
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.radillo.playerwave.R
import com.radillo.playerwave.core.ui.ArtworkLoader
import com.radillo.playerwave.core.util.formatDuration
import com.radillo.playerwave.databinding.FragmentNowPlayingBinding
import com.radillo.playerwave.player.engine.PlaybackState
import com.radillo.playerwave.player.engine.RepeatMode
import com.radillo.playerwave.ui.ChromeMode
import com.radillo.playerwave.ui.common.collectWhenStarted
import com.radillo.playerwave.ui.common.onUserChange
import com.radillo.playerwave.ui.common.AccentColors
import com.radillo.playerwave.ui.common.setMarqueeText
import com.radillo.playerwave.ui.common.subtitle
import com.radillo.playerwave.ui.common.visibleIf
import com.radillo.playerwave.ui.common.openSystemEqualizer
import com.radillo.playerwave.ui.library.TrackActionsViewModel
import com.radillo.playerwave.ui.library.showTrackActions
import com.radillo.playerwave.ui.navigator
import com.radillo.playerwave.ui.visualizer.VisualizerViewModel
import com.radillo.playerwave.ui.visualizer.sceneLabelRes
import kotlin.math.abs
import dagger.hilt.android.AndroidEntryPoint

/** Pantalla «Reproduciendo»: carátula, progreso, controles, favorito, temporizador, ecualizador y cola. */
@AndroidEntryPoint
class NowPlayingFragment : Fragment(R.layout.fragment_now_playing), ChromeMode {

    private companion object {
        const val KEY_ART_VISUALIZER = "now_playing_visualizer"
        private const val KEY_ART_HINT_SHOWN = "now_playing_visualizer_hint"
        private const val SCENE_LABEL_MS = 1_500L
        private const val HINT_MS = 4_500L
        /** Deslizamiento mínimo (fracción del ancho del recuadro) para cambiar de visualizador. */
        private const val SWIPE_FRACTION = 0.15f
    }

    override val hidesMiniPlayer: Boolean get() = true

    private val viewModel: PlayerViewModel by activityViewModels()
    private val trackActions: TrackActionsViewModel by activityViewModels()
    /** Aplica escena, paleta y perfil al motor aunque no se haya abierto la pestaña Visualizador. */
    private val visualizer: VisualizerViewModel by viewModels()
    private var binding: FragmentNowPlayingBinding? = null
    private var dragging = false
    private val handler = Handler(Looper.getMainLooper())
    private val hideSceneLabel = Runnable { binding?.artSceneLabel?.visibleIf(false) }
    private var lastArt: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentNowPlayingBinding.bind(view)
        binding = b
        b.btnPlay.setOnClickListener { viewModel.togglePlayPause() }
        b.btnNext.setOnClickListener { viewModel.next() }
        b.btnPrevious.setOnClickListener { viewModel.previous() }
        b.btnShuffle.setOnClickListener { viewModel.toggleShuffle() }
        b.btnRepeat.setOnClickListener { viewModel.cycleRepeat() }
        b.btnFavorite.setOnClickListener { viewModel.toggleFavorite() }
        b.btnCrossfade.setOnClickListener {
            val seconds = viewModel.toggleCrossfade()
            Toast.makeText(
                requireContext(),
                if (seconds > 0) getString(R.string.toast_crossfade_on, seconds) else getString(R.string.toast_crossfade_off),
                Toast.LENGTH_SHORT,
            ).show()
        }
        b.btnEqualizer.setOnClickListener { openSystemEqualizer() }
        b.btnQueue.setOnClickListener { navigator.push(QueueFragment()) }
        // El temporizador de apagado pasó al menú «⋮» (su lugar en las tarjetas es ahora el crossfade).
        b.btnMore.setOnClickListener {
            viewModel.state.value.currentTrack?.let {
                showTrackActions(it, trackActions, listOf(ExtraAction(R.string.sleep_timer) { showSleepDialog() }))
            }
        }

        // El recuadro de la carátula alterna con el visualizador en vivo (un toque cambia).
        b.artVisualizer.setEngine(visualizer.engine)
        val visualizerInArt = prefs().getBoolean(KEY_ART_VISUALIZER, true)
        showVisualizerInArt(visualizerInArt)
        if (visualizerInArt) showArtHintOnce()
        // Esquinas redondeadas de la carátula (el fondo redondeado no recorta la imagen por sí solo).
        b.artFrame.clipToOutline = true
        // Lectores de pantalla y teclado: «activar» alterna carátula y visualizador.
        b.artFrame.setOnClickListener { toggleArtVisualizer() }
        setUpArtGestures(b)
        b.seek.onUserChange(
            onChange = { progress ->
                val duration = viewModel.state.value.durationMs
                b.position.text = formatDuration(progress * duration / 1000L)
            },
            onStart = { dragging = true },
            onStop = {
                dragging = false
                val duration = viewModel.state.value.durationMs
                viewModel.seekTo(b.seek.progress * duration / 1000L)
            },
        )

        collectWhenStarted(viewModel.state, ::render)
        collectWhenStarted(viewModel.isFavorite) { fav ->
            b.btnFavoriteIcon.setImageResource(if (fav) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
            b.btnFavorite.isActivated = fav
            b.btnFavorite.contentDescription = getString(if (fav) R.string.action_remove_favorite else R.string.action_add_favorite)
        }
        collectWhenStarted(viewModel.crossfadeSeconds) { seconds ->
            // Encendido: tarjeta en color de acento y la duración a la vista (el estado se ve donde se cambia).
            b.btnCrossfade.isActivated = seconds > 0
            b.btnCrossfadeLabel.text = if (seconds > 0) getString(R.string.np_crossfade_on, seconds) else getString(R.string.np_crossfade)
        }
        collectWhenStarted(viewModel.accent) { accent ->
            val color = AccentColors.visible(
                accent ?: requireContext().getColor(R.color.accent),
                requireContext().getColor(R.color.window_background),
            )
            b.btnPlay.backgroundTintList = ColorStateList.valueOf(color)
            b.seek.progressTintList = ColorStateList.valueOf(color)
            b.seek.thumbTintList = ColorStateList.valueOf(color)
        }
    }

    override fun onDestroyView() {
        handler.removeCallbacks(hideSceneLabel)
        binding = null
        lastArt = null
        lastPlaying = null
        lastRendered = null
        super.onDestroyView()
    }

    /** Evita repintar iconos dos veces por segundo (el estado cambia con cada tic de progreso). */
    private var lastPlaying: Boolean? = null
    /** Último estado pintado, sin la posición. */
    private var lastRendered: PlaybackState? = null

    private fun render(state: PlaybackState) {
        val b = binding ?: return
        val track = state.currentTrack
        b.contentGroup.visibleIf(track != null)
        b.btnMore.visibleIf(track != null)
        b.emptyText.visibleIf(track == null)
        if (track == null) return
        if (lastArt != track.coverUri) {
            lastArt = track.coverUri
            ArtworkLoader.get(requireContext()).load(b.art, track.coverUri, b.artFrame.layoutParams.width, R.drawable.tile_note)
        }
        // Textos, iconos y botones solo cuando cambia algo más que la posición (no a cada tic).
        val withoutPosition = state.copy(positionMs = 0L)
        if (withoutPosition != lastRendered) {
            lastRendered = withoutPosition
            b.trackTitle.setMarqueeText(track.title)
            b.trackSubtitle.setMarqueeText(track.subtitle(requireContext()))
            if (lastPlaying != state.isPlaying) {
                lastPlaying = state.isPlaying
                b.btnPlay.setImageResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
                b.btnPlay.contentDescription = getString(if (state.isPlaying) R.string.action_pause else R.string.action_play)
            }
            val next = state.queue.getOrNull(state.queueIndex + 1)
                ?: if (state.repeatMode == RepeatMode.ALL) state.queue.firstOrNull() else null
            b.queueInfo.text = next?.let { getString(R.string.np_up_next, it.title) }.orEmpty()
            b.btnShuffle.isActivated = state.shuffleEnabled
            b.btnRepeat.isActivated = state.repeatMode != RepeatMode.OFF
            b.btnRepeat.setImageResource(if (state.repeatMode == RepeatMode.ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat)
            b.duration.text = formatDuration(state.durationMs)
        }
        if (!dragging) {
            val duration = state.durationMs.coerceAtLeast(1L)
            b.seek.progress = (state.positionMs * 1000L / duration).toInt().coerceIn(0, 1000)
            b.position.text = formatDuration(state.positionMs)
        }
    }

    private fun prefs() = requireContext().getSharedPreferences("ui_state", android.content.Context.MODE_PRIVATE)

    /** Visualizador o carátula en el recuadro. Oculto, el SurfaceView libera su superficie y su hilo. */
    private fun showVisualizerInArt(show: Boolean) {
        val b = binding ?: return
        b.artVisualizer.visibleIf(show)
        b.art.visibleIf(!show)
        if (!show) {
            handler.removeCallbacks(hideSceneLabel)
            b.artSceneLabel.visibleIf(false)
        }
    }

    private fun toggleArtVisualizer() {
        val b = binding ?: return
        val next = b.artVisualizer.visibility != View.VISIBLE
        prefs().edit().putBoolean(KEY_ART_VISUALIZER, next).apply()
        showVisualizerInArt(next)
        if (next) showArtHintOnce()
    }

    /** La primera vez que aparece el visualizador en el recuadro se explica cómo cambiarlo desde aquí. */
    private fun showArtHintOnce() {
        if (prefs().getBoolean(KEY_ART_HINT_SHOWN, false)) return
        prefs().edit().putBoolean(KEY_ART_HINT_SHOWN, true).apply()
        showSceneLabel(getString(R.string.np_visualizer_hint), HINT_MS)
    }

    /**
     * Gestos del recuadro:
     * - con la carátula, un toque muestra el visualizador (al instante);
     * - con el visualizador, **doble toque** o **deslizar** a los lados cambia de visualizador y
     *   **un toque** vuelve a la carátula (se confirma tras el margen del doble toque).
     */
    @SuppressLint("ClickableViewAccessibility") // la activación accesible va por el OnClickListener
    private fun setUpArtGestures(b: FragmentNowPlayingBinding) {
        val listener = object : GestureDetector.SimpleOnGestureListener() {
            /** El toque ya hizo su efecto al levantar el dedo (mostrar el visualizador). */
            private var handledOnUp = false

            override fun onDown(e: MotionEvent): Boolean {
                handledOnUp = false
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (b.artVisualizer.visibility != View.VISIBLE) {
                    handledOnUp = true
                    toggleArtVisualizer()
                }
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!handledOnUp && b.artVisualizer.visibility == View.VISIBLE) toggleArtVisualizer()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (b.artVisualizer.visibility == View.VISIBLE) stepArtScene(1)
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null || b.artVisualizer.visibility != View.VISIBLE) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (abs(dx) < b.artFrame.width * SWIPE_FRACTION || abs(dx) < abs(dy) * 1.5f) return false
                stepArtScene(if (dx < 0) 1 else -1)
                return true
            }
        }
        val detector = GestureDetector(requireContext(), listener)
        b.artFrame.setOnTouchListener { _, event -> detector.onTouchEvent(event) }
    }

    /** Visualizador siguiente o anterior, en bucle, sin salir de «Reproduciendo». */
    private fun stepArtScene(delta: Int) {
        val ids = visualizer.sceneIds
        if (ids.isEmpty()) return
        val current = ids.indexOf(visualizer.settings.value.sceneId).coerceAtLeast(0)
        val next = ids[(current + delta).mod(ids.size)]
        visualizer.selectScene(next)
        showSceneLabel(getString(sceneLabelRes(next)), SCENE_LABEL_MS)
    }

    private fun showSceneLabel(text: String, millis: Long) {
        val b = binding ?: return
        b.artSceneLabel.text = text
        b.artSceneLabel.visibleIf(true)
        handler.removeCallbacks(hideSceneLabel)
        handler.postDelayed(hideSceneLabel, millis)
    }

    private fun showSleepDialog() {
        val minutes = intArrayOf(10, 15, 30, 45, 60, 90)
        val remaining = viewModel.sleepRemainingMs.value
        val labels = minutes.map { getString(R.string.sleep_timer_minutes, it) }.toMutableList()
        if (remaining != null) labels += getString(R.string.sleep_timer_cancel)
        val builder = AlertDialog.Builder(requireContext())
            .setTitle(
                if (remaining != null) getString(R.string.sleep_timer_remaining, formatDuration(remaining))
                else getString(R.string.sleep_timer),
            )
            .setItems(labels.toTypedArray()) { _, which ->
                if (which < minutes.size) viewModel.startSleepTimer(minutes[which]) else viewModel.cancelSleepTimer()
            }
            .setNegativeButton(R.string.action_cancel, null)
        builder.show()
    }
}
