package com.radillo.playerwave.ui.player

import com.radillo.playerwave.ui.library.ExtraAction
import android.widget.Toast
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewConfiguration
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
import com.radillo.playerwave.ui.common.MultiTapDetector
import com.radillo.playerwave.ui.common.AccentColors
import com.radillo.playerwave.ui.common.setMarqueeText
import com.radillo.playerwave.ui.common.subtitle
import com.radillo.playerwave.ui.common.visibleIf
import com.radillo.playerwave.ui.common.openSystemEqualizer
import com.radillo.playerwave.ui.library.TrackActionsViewModel
import com.radillo.playerwave.ui.library.showTrackActions
import com.radillo.playerwave.ui.navigator
import com.radillo.playerwave.ui.visualizer.VisualizerFragment
import com.radillo.playerwave.ui.visualizer.VisualizerSettingsDialog
import com.radillo.playerwave.ui.visualizer.VisualizerViewModel
import com.radillo.playerwave.ui.visualizer.sceneLabelRes
import kotlin.math.abs
import dagger.hilt.android.AndroidEntryPoint

/**
 * Pantalla «Reproduciendo»: carátula o visualizador (con favorito o ajustes en su esquina), progreso
 * como onda de sonido, controles, crossfade, ecualizador y cola.
 */
@AndroidEntryPoint
class NowPlayingFragment : Fragment(R.layout.fragment_now_playing), ChromeMode {

    companion object {
        private const val KEY_ART_VISUALIZER = "now_playing_visualizer"
        private const val KEY_ART_HINT_SHOWN = "now_playing_visualizer_hint"
        private const val SCENE_LABEL_MS = 1_500L
        private const val HINT_MS = 4_500L
        /** Deslizamiento mínimo (fracción del ancho del recuadro) para cambiar de visualizador. */
        private const val SWIPE_FRACTION = 0.15f
        /** Margen para saber si a un toque le sigue otro (uno, dos o tres toques). */
        private const val TAP_WINDOW_MS = 280L
        /** Corazón de favorito: rojo fijo (no sigue al color de la portada). */
        private const val FAVORITE_RED = 0xFFFF2D3D.toInt()

        /**
         * Un solo color «activo» en toda la pantalla (el de la portada): reproducir, progreso,
         * aleatorio, repetir y crossfade encendido. El corazón de favorito es la excepción: siempre rojo.
         */
        fun applyAccent(b: FragmentNowPlayingBinding, color: Int) {
            val ctx = b.root.context
            val density = ctx.resources.displayMetrics.density
            b.btnPlay.backgroundTintList = ColorStateList.valueOf(color)
            b.btnPlay.imageTintList = ColorStateList.valueOf(AccentColors.onAccent(color))
            b.seek.accentColor = color
            val states = AccentColors.activatedStates(color, ctx.getColor(R.color.text_primary))
            listOf(b.btnShuffle, b.btnRepeat).forEach {
                it.background = AccentColors.toggleBackground(color, density)
                it.imageTintList = states
            }
            b.btnCrossfadeIcon.imageTintList = states
            b.btnCrossfadeLabel.setTextColor(states)
        }

        /** Corazón de la carátula: silueta blanca; relleno y rojo si la canción es favorita. */
        fun renderFavorite(b: FragmentNowPlayingBinding, favorite: Boolean) {
            b.btnFavorite.setImageResource(if (favorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
            b.btnFavorite.imageTintList = ColorStateList.valueOf(if (favorite) FAVORITE_RED else 0xFFFFFFFF.toInt())
            b.btnFavorite.contentDescription =
                b.root.context.getString(if (favorite) R.string.action_remove_favorite else R.string.action_add_favorite)
        }
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
        b.seek.listener = object : WaveformSeekView.Listener {
            override fun onStart() {
                dragging = true
            }

            override fun onChange(fraction: Float) {
                b.position.text = formatDuration((fraction * viewModel.state.value.durationMs).toLong())
            }

            override fun onStop(fraction: Float, seek: Boolean) {
                dragging = false
                if (seek) viewModel.seekTo((fraction * viewModel.state.value.durationMs).toLong())
            }
        }
        b.btnVisualizerSettings.setOnClickListener { VisualizerSettingsDialog.show(this, visualizer) }

        collectWhenStarted(viewModel.state, ::render)
        collectWhenStarted(viewModel.isFavorite) { renderFavorite(b, it) }
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
            applyAccent(b, color)
        }
        collectWhenStarted(viewModel.waveform) { b.seek.peaks = it }
    }

    override fun onDestroyView() {
        handler.removeCallbacks(hideSceneLabel)
        artTaps?.reset()
        artTaps = null
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
            b.queueInfo.text = next?.let { getString(R.string.np_next_short, it.title) }.orEmpty()
            b.btnShuffle.isActivated = state.shuffleEnabled
            b.btnRepeat.isActivated = state.repeatMode != RepeatMode.OFF
            b.btnRepeat.setImageResource(if (state.repeatMode == RepeatMode.ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat)
            b.duration.text = formatDuration(state.durationMs)
        }
        if (!dragging) {
            val duration = state.durationMs.coerceAtLeast(1L)
            b.seek.fraction = state.positionMs.toFloat() / duration
            b.position.text = formatDuration(state.positionMs)
        }
    }

    private fun prefs() = requireContext().getSharedPreferences("ui_state", android.content.Context.MODE_PRIVATE)

    /** Visualizador o carátula en el recuadro. Oculto, el SurfaceView libera su superficie y su hilo. */
    private fun showVisualizerInArt(show: Boolean) {
        val b = binding ?: return
        b.artVisualizer.visibleIf(show)
        b.art.visibleIf(!show)
        // Esquina del recuadro: corazón con la carátula, engrane de ajustes con el visualizador.
        b.btnFavorite.visibleIf(!show)
        b.btnVisualizerSettings.visibleIf(show)
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
     * Gestos del recuadro (iguales con carátula o visualizador):
     * - **un toque** alterna carátula y visualizador;
     * - **dos toques** (o **deslizar** a los lados) cambian de visualizador;
     * - **tres toques** abren el visualizador a pantalla completa.
     * Para distinguirlos, el efecto de uno o dos toques espera [TAP_WINDOW_MS] por si llega otro.
     */
    @SuppressLint("ClickableViewAccessibility") // la activación accesible va por el OnClickListener
    private fun setUpArtGestures(b: FragmentNowPlayingBinding) {
        val taps = MultiTapDetector(
            windowMs = TAP_WINDOW_MS,
            maxTaps = 3,
            schedule = { r, ms -> handler.postDelayed(r, ms) },
            cancel = handler::removeCallbacks,
            onTaps = ::onArtTaps,
        )
        artTaps = taps
        val slop = ViewConfiguration.get(requireContext()).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        b.artFrame.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    when {
                        abs(dx) <= slop && abs(dy) <= slop -> taps.tap()
                        abs(dx) >= view.width * SWIPE_FRACTION && abs(dx) >= abs(dy) * 1.5f &&
                            b.artVisualizer.visibility == View.VISIBLE -> {
                            taps.reset()
                            stepArtScene(if (dx < 0) 1 else -1)
                        }
                    }
                }
            }
            true
        }
    }

    private var artTaps: MultiTapDetector? = null

    private fun onArtTaps(count: Int) {
        val b = binding ?: return
        val showingVisualizer = b.artVisualizer.visibility == View.VISIBLE
        when {
            count >= 3 -> navigator.push(VisualizerFragment.newInstance())
            count == 2 && showingVisualizer -> stepArtScene(1)
            else -> toggleArtVisualizer()
        }
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
