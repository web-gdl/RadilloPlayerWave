package com.radillo.playerwave.visualizer.engine

import android.graphics.Canvas
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
 * - la lógica visual (las [Scene]s),
 * - el renderizado (quien llame a [render] con un lienzo, normalmente el hilo de render de
 *   `VisualizerSurfaceView`).
 *
 * El análisis solo corre mientras hay al menos una superficie adjunta ([attach]/[detach]).
 * Las escenas se crean desde el [SceneRegistry]; cambiar de escena hace un fundido a través del
 * fondo (sin capas fuera de pantalla, aptas para GPUs modestas).
 *
 * Hilos: la configuración llega del hilo principal y el render de su propio hilo; todo el estado
 * de escenas se protege con un único cerrojo, de modo que ninguna escena se toca desde dos hilos
 * a la vez.
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
    private val lock = Any()

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

    /** Fotogramas renderizados por segundo, medidos (se actualiza una vez por segundo). */
    private val _fps = MutableStateFlow(0f)
    val fps: StateFlow<Float> = _fps
    private var fpsWindowStartNanos = 0L
    private var fpsWindowFrames = 0

    /** Tope de fotogramas por segundo; 0 = sin tope (el de la pantalla). */
    @Volatile private var maxFps = 0
    private var deviceScale = 1f

    // ---- Estado de render (protegido por `lock`) ----
    private var scene: Scene? = null
    private var outgoing: Scene? = null
    private var transition = 1f
    /** Parámetros elegidos por el usuario (quality y deviceScale siempre 1) y los efectivos. */
    private var userParams = SceneParams()
    private var params = SceneParams()
    private var width = 0
    private var height = 0
    private var lastFrameNanos = 0L
    private var lastShown: AudioFrame = synthetic.silent()
    private var latencyNanos = DEFAULT_LATENCY_NANOS
    private var syncOffsetUs = 0L

    /** Latencia estimada entre el análisis y el sonido, en ms (diagnóstico). */
    val estimatedLatencyMs: Int get() = (latencyNanos / 1_000_000L).toInt()

    /**
     * Intervalo mínimo entre frames en nanosegundos según el tope de fps, o 0 si no hay tope.
     * El hilo de render lo usa para espaciar los frames.
     */
    val minFrameIntervalNanos: Long
        get() {
            val fps = maxFps
            return if (fps > 0) 1_000_000_000L / fps else 0L
        }

    // ---- Ciclo de vida ----

    fun attach() {
        synchronized(lock) {
            attachedSurfaces++
            if (analysisJob == null) startAnalysis()
        }
    }

    fun detach() {
        synchronized(lock) {
            attachedSurfaces = (attachedSurfaces - 1).coerceAtLeast(0)
            if (attachedSurfaces == 0) {
                analysisJob?.cancel()
                analysisJob = null
            }
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

    // ---- Configuración (cualquier hilo) ----

    fun setScene(id: String) {
        synchronized(lock) {
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
    }

    fun setParams(newParams: SceneParams) {
        synchronized(lock) {
            val normalized = newParams.copy(quality = 1f, deviceScale = 1f)
            if (normalized == userParams) return
            userParams = normalized
            applyParams(effectiveParams())
        }
    }

    /** Factor fijo del perfil del dispositivo (0.6 en gama baja) que reduce la densidad de las escenas. */
    fun setDeviceScale(scale: Float) {
        synchronized(lock) {
            val clamped = scale.coerceIn(0.2f, 1f)
            if (clamped == deviceScale) return
            deviceScale = clamped
            applyParams(effectiveParams())
        }
    }

    /** Calidad mínima a la que puede bajar el gobernador. */
    fun setMinQuality(min: Float) {
        synchronized(lock) {
            governor.minQuality = min
            applyParams(effectiveParams())
        }
    }

    /**
     * Limita los fotogramas por segundo (p. ej. 30 en hardware modesto: mitad de CPU y GPU, menos
     * calor). 0 o un valor ≥ al de la pantalla no tienen efecto.
     */
    fun setMaxFps(fps: Int) {
        synchronized(lock) {
            val clamped = fps.coerceIn(0, 240)
            if (clamped == maxFps) return
            maxFps = clamped
            governor.targetFrameSeconds = if (clamped > 0) 1f / clamped else 0f
        }
    }

    /**
     * Activa o desactiva el gobernador de calidad. Desactivado, las escenas usan siempre la
     * densidad elegida por el usuario.
     */
    fun setAutoQuality(enabled: Boolean) {
        synchronized(lock) {
            if (governor.enabled == enabled) return
            governor.enabled = enabled
            applyParams(effectiveParams())
        }
    }

    fun setSyncOffsetMs(ms: Int) {
        synchronized(lock) {
            syncOffsetUs = ms.toLong() * 1_000L
        }
    }

    fun onResize(widthPx: Int, heightPx: Int) {
        synchronized(lock) {
            if (widthPx == width && heightPx == height) return
            width = widthPx
            height = heightPx
            scene?.onResize(widthPx, heightPx)
            outgoing?.onResize(widthPx, heightPx)
            governor.reset()
        }
    }

    private fun effectiveParams(): SceneParams = userParams.copy(quality = governor.quality, deviceScale = deviceScale)

    private fun applyParams(effective: SceneParams) {
        if (effective == params) return
        params = effective
        _quality.value = effective.quality
        scene?.onParamsChanged(effective)
        outgoing?.onParamsChanged(effective)
    }

    // ---- Render (hilo de render) ----

    /**
     * Avanza la simulación y dibuja un frame completo (fondo, fundido y escena) en [canvas].
     * [nowNanos] es `System.nanoTime()` al empezar el frame.
     */
    fun render(canvas: Canvas, nowNanos: Long) {
        synchronized(lock) {
            step(nowNanos)
            draw(canvas)
        }
    }

    private fun step(nowNanos: Long) {
        measureFps(nowNanos)
        val rawDt = if (lastFrameNanos == 0L) 1f / 60f else (nowNanos - lastFrameNanos) / 1e9f
        val dt = rawDt.coerceIn(0.001f, 0.1f)
        lastFrameNanos = nowNanos

        // El gobernador decide con la duración real del frame (una pausa larga no cuenta como tardío).
        governor.onFrame(rawDt)?.let { q -> applyParams(userParams.copy(quality = q, deviceScale = deviceScale)) }

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

    private fun measureFps(nowNanos: Long) {
        if (fpsWindowStartNanos == 0L) {
            fpsWindowStartNanos = nowNanos
            fpsWindowFrames = 0
            return
        }
        fpsWindowFrames++
        val elapsed = nowNanos - fpsWindowStartNanos
        if (elapsed >= 1_000_000_000L) {
            _fps.value = fpsWindowFrames * 1e9f / elapsed
            fpsWindowStartNanos = nowNanos
            fpsWindowFrames = 0
        }
    }

    private fun pickFrame(nowNanos: Long, dt: Float): AudioFrame {
        val playing = safeIsPlaying()
        val stale = nowNanos - lastRealFrameNanos > STALE_NANOS
        val unavailable = source.status.value == AudioSourceStatus.UNAVAILABLE

        if (!stale && !unavailable) {
            val position = safePositionUs()?.let { it + syncOffsetUs }
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
            synthetic.ambient(dt, safePositionUs() ?: 0L)
        } else {
            _dataState.value = DataState.IDLE
            synthetic.decay(lastShown, dt)
        }
    }

    // Red de seguridad: un reloj que falle no debe dejar la pantalla sin dibujar.
    private fun safeIsPlaying(): Boolean = try {
        clock.isPlaying()
    } catch (_: RuntimeException) {
        false
    }

    private fun safePositionUs(): Long? = try {
        clock.positionUs()
    } catch (_: RuntimeException) {
        null
    }

    /**
     * Fondo, escena y fundido. El cambio de escena se hace «a través del fondo»: la escena saliente
     * se oscurece y la nueva emerge de la oscuridad, con un solo rectángulo translúcido por frame
     * en vez de capas fuera de pantalla.
     */
    private fun draw(canvas: Canvas) {
        val palette = Palettes.byId(params.paletteId)
        canvas.drawColor(palette.background)
        val current = scene ?: return
        val out = outgoing
        if (out == null || transition >= 1f) {
            current.draw(canvas)
            return
        }
        if (transition < 0.5f) {
            out.draw(canvas)
            canvas.drawColor(Argb.withAlpha(palette.background, transition * 2f))
        } else {
            current.draw(canvas)
            canvas.drawColor(Argb.withAlpha(palette.background, (1f - transition) * 2f))
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
