package com.radillo.playerwave.visualizer.engine

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.radillo.playerwave.visualizer.analysis.AnalyzerConfig
import com.radillo.playerwave.visualizer.analysis.AudioAnalyzer
import com.radillo.playerwave.visualizer.analysis.AudioFrame
import com.radillo.playerwave.visualizer.analysis.AudioSource
import com.radillo.playerwave.visualizer.analysis.AudioSourceStatus
import com.radillo.playerwave.visualizer.analysis.FrameHistory
import com.radillo.playerwave.visualizer.analysis.PlaybackClock
import com.radillo.playerwave.visualizer.analysis.SyntheticFrameSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.min

/** Qué están viendo las escenas: audio real alineado, decaimiento en pausa, o respaldo sintético. */
enum class DataState { LIVE, IDLE, SYNTHETIC }

/**
 * Motor de visualización. Une tres piezas que no se conocen entre sí:
 * - el análisis (coroutine en `Dispatchers.Default`, alimentada por [AudioSource]),
 * - la lógica visual (las [Scene]s, actualizadas en el hilo de render),
 * - el renderizado (quien llame a [onFrame] y [draw], normalmente [VisualizerSurface]).
 *
 * El análisis solo corre mientras hay al menos una superficie adjunta ([attach]/[detach]).
 * Las escenas se crean desde el [SceneRegistry]; cambiar de escena hace un fundido cruzado.
 */
class VisualizerEngine(
    private val source: AudioSource,
    private val clock: PlaybackClock,
    private val registry: SceneRegistry,
    private val analyzerConfig: AnalyzerConfig = AnalyzerConfig(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val analyzer = AudioAnalyzer(analyzerConfig)
    private val history = FrameHistory()
    private val synthetic = SyntheticFrameSource(analyzerConfig)

    private var analysisJob: Job? = null
    private var attachedSurfaces = 0

    @Volatile private var lastRealFrameNanos = 0L

    private val _dataState = MutableStateFlow(DataState.IDLE)
    val dataState: StateFlow<DataState> = _dataState
    val audioStatus: StateFlow<AudioSourceStatus> get() = source.status

    private val _currentSceneId = MutableStateFlow<String?>(null)
    val currentSceneId: StateFlow<String?> = _currentSceneId
    val sceneIds: List<String> get() = registry.ids

    /** Factor de calidad vigente (1 = completo) decidido por el [QualityGovernor]. */
    private val _quality = MutableStateFlow(1f)
    val quality: StateFlow<Float> = _quality
    private val governor = QualityGovernor()

    // ---- Estado de render (solo hilo de render) ----
    private var scene: Scene? = null
    private var outgoing: Scene? = null
    private var transition = 1f
    /** Parámetros elegidos por el usuario (quality siempre 1) y los efectivos (con la calidad del gobernador). */
    private var userParams = SceneParams()
    private var params = SceneParams()
    private var width = 0
    private var height = 0
    private var lastFrameNanos = 0L
    private var lastShown: AudioFrame = synthetic.silent()
    private var latencyNanos = DEFAULT_LATENCY_NANOS
    private var syncOffsetUs = 0L
    private var layerBounds = Rect.Zero
    private val layerPaint = Paint()

    /** Latencia estimada entre el análisis y el sonido, en ms (diagnóstico). */
    val estimatedLatencyMs: Int get() = (latencyNanos / 1_000_000L).toInt()

    // ---- Ciclo de vida ----

    fun attach() {
        attachedSurfaces++
        if (analysisJob == null) startAnalysis()
    }

    fun detach() {
        attachedSurfaces = (attachedSurfaces - 1).coerceAtLeast(0)
        if (attachedSurfaces == 0) {
            analysisJob?.cancel()
            analysisJob = null
        }
    }

    private fun startAnalysis() {
        analysisJob = scope.launch {
            analyzer.reset()
            source.blocks.collect { block ->
                analyzer.push(block) { frame ->
                    val now = System.nanoTime()
                    history.add(frame, now)
                    lastRealFrameNanos = now
                }
            }
        }
    }

    // ---- Configuración (hilo de render / principal) ----

    fun setScene(id: String) {
        if (id == scene?.id) return
        val next = registry.create(id) ?: return
        next.onAttach(params)
        if (width > 0 && height > 0) next.onResize(width, height)
        val previous = scene
        if (previous != null) {
            outgoing?.onDetach()
            outgoing = previous
            transition = 0f
        }
        scene = next
        _currentSceneId.value = id
        governor.reset()
    }

    fun setParams(newParams: SceneParams) {
        val normalized = newParams.copy(quality = 1f)
        if (normalized == userParams) return
        userParams = normalized
        applyParams(normalized.copy(quality = governor.quality))
    }

    /**
     * Activa o desactiva el gobernador de calidad. Desactivado, las escenas usan siempre la
     * densidad elegida por el usuario.
     */
    fun setAutoQuality(enabled: Boolean) {
        if (governor.enabled == enabled) return
        governor.enabled = enabled
        applyParams(userParams.copy(quality = governor.quality))
    }

    private fun applyParams(effective: SceneParams) {
        if (effective == params) return
        params = effective
        _quality.value = effective.quality
        scene?.onParamsChanged(effective)
        outgoing?.onParamsChanged(effective)
    }

    fun setSyncOffsetMs(ms: Int) {
        syncOffsetUs = ms.toLong() * 1_000L
    }

    fun onResize(widthPx: Int, heightPx: Int) {
        if (widthPx == width && heightPx == height) return
        width = widthPx
        height = heightPx
        layerBounds = Rect(0f, 0f, widthPx.toFloat(), heightPx.toFloat())
        scene?.onResize(widthPx, heightPx)
        outgoing?.onResize(widthPx, heightPx)
        governor.reset()
    }

    // ---- Render ----

    /** Avanza la simulación un frame. Llamar desde el hilo de render antes de [draw]. */
    fun onFrame(nowNanos: Long) {
        val rawDt = if (lastFrameNanos == 0L) 1f / 60f else (nowNanos - lastFrameNanos) / 1e9f
        val dt = rawDt.coerceIn(0.001f, 0.1f)
        lastFrameNanos = nowNanos

        // El gobernador decide con la duración real del frame (una pausa larga no cuenta como tardío).
        governor.onFrame(rawDt)?.let { q -> applyParams(userParams.copy(quality = q)) }

        val frame = pickFrame(nowNanos, dt)
        lastShown = frame

        scene?.update(frame, dt)
        val out = outgoing
        if (out != null) {
            out.update(frame, dt)
            transition = min(1f, transition + dt / TRANSITION_SECONDS)
            if (transition >= 1f) {
                out.onDetach()
                outgoing = null
            }
        }
    }

    private fun pickFrame(nowNanos: Long, dt: Float): AudioFrame {
        val playing = clock.isPlaying()
        val stale = nowNanos - lastRealFrameNanos > STALE_NANOS
        val unavailable = source.status.value == AudioSourceStatus.UNAVAILABLE

        if (!stale && !unavailable) {
            val position = clock.positionUs()?.let { it + syncOffsetUs }
            val selection = history.select(position, nowNanos, latencyNanos)
            if (selection != null) {
                if (selection.matchedByTimestamp) {
                    val measured = (nowNanos - selection.entry.arrivalNanos).coerceIn(0L, MAX_LATENCY_NANOS)
                    latencyNanos += ((measured - latencyNanos) * LATENCY_SMOOTHING).toLong()
                }
                _dataState.value = DataState.LIVE
                return selection.entry.frame
            }
        }
        return if (playing && (unavailable || stale)) {
            _dataState.value = DataState.SYNTHETIC
            synthetic.ambient(dt, clock.positionUs() ?: 0L)
        } else {
            _dataState.value = DataState.IDLE
            synthetic.decay(lastShown, dt)
        }
    }

    /** Dibuja fondo, escena saliente (fundido) y escena actual. */
    fun draw(scope: DrawScope) {
        val palette = Palettes.byId(params.paletteId)
        scope.drawRect(color = palette.background)
        val current = scene ?: return
        val out = outgoing
        if (out == null || transition >= 1f) {
            current.draw(scope)
            return
        }
        scope.drawIntoCanvas { canvas ->
            layerPaint.alpha = 1f - transition
            canvas.saveLayer(layerBounds, layerPaint)
            out.draw(scope)
            canvas.restore()
            layerPaint.alpha = transition
            canvas.saveLayer(layerBounds, layerPaint)
            current.draw(scope)
            canvas.restore()
        }
    }

    companion object {
        private const val TRANSITION_SECONDS = 0.6f
        private const val STALE_NANOS = 400_000_000L
        private const val DEFAULT_LATENCY_NANOS = 300_000_000L
        private const val MAX_LATENCY_NANOS = 2_000_000_000L
        private const val LATENCY_SMOOTHING = 0.05f
    }
}
