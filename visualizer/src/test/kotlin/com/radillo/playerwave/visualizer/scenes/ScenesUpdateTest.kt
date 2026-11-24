package com.radillo.playerwave.visualizer.scenes

import android.graphics.Canvas
import com.google.common.truth.Truth.assertThat
import com.radillo.playerwave.visualizer.analysis.AnalyzerConfig
import com.radillo.playerwave.visualizer.analysis.AudioFrame
import com.radillo.playerwave.visualizer.analysis.SyntheticFrameSource
import com.radillo.playerwave.visualizer.engine.DefaultScenes
import com.radillo.playerwave.visualizer.engine.RenderBackend
import com.radillo.playerwave.visualizer.engine.SceneParams
import org.junit.Test
import kotlin.random.Random

/**
 * Ejercita el ciclo de vida, la simulación y el dibujo de todas las escenas incluidas. El lienzo
 * es el stub del SDK (sus métodos no hacen nada), así que `draw` prueba la lógica de recorrido
 * de buffers y lotes, no el resultado visual. Comprueba que nada lanza con frames reales,
 * sintéticos, silenciosos y tamaños extremos, y que parámetros y calidad reconstruyen sin fallos.
 */
class ScenesUpdateTest {

    private val config = AnalyzerConfig()
    private val synthetic = SyntheticFrameSource(config)
    private val random = Random(3)
    private val canvas = Canvas()

    private fun loudFrame(onset: Float = 0f): AudioFrame {
        val bands = FloatArray(config.bandCount) { random.nextFloat() }
        return AudioFrame(
            spectrum = FloatArray(config.spectrumSize) { random.nextFloat() * 0.5f },
            bands = bands,
            bass = 0.8f,
            mid = 0.5f,
            treble = 0.6f,
            rms = 0.7f,
            peak = 0.95f,
            onset = onset,
            waveformLeft = FloatArray(config.waveformSize) { (random.nextFloat() - 0.5f) * 1.6f },
            waveformRight = FloatArray(config.waveformSize) { (random.nextFloat() - 0.5f) * 1.6f },
            timestampUs = 0L,
            synthetic = false,
        )
    }

    @Test
    fun `registry exposes the four scenes in order with unique ids and canvas backend`() {
        val registry = DefaultScenes.registry()
        assertThat(registry.ids).containsExactly(
            SpectrumBarsScene.ID, RadialSpectrumScene.ID, OscilloscopeScene.ID, TerrainScene.ID,
        ).inOrder()
        assertThat(registry.ids.toSet()).hasSize(registry.ids.size)
        for (id in registry.ids) {
            val scene = registry.create(id)
            assertThat(scene).isNotNull()
            assertThat(scene!!.id).isEqualTo(id)
            assertThat(scene.backend).isEqualTo(RenderBackend.CANVAS)
            assertThat(scene.displayName).isNotEmpty()
        }
        assertThat(registry.ids).contains(DefaultScenes.DEFAULT_ID)
    }

    @Test
    fun `every scene survives attach, resize, hundreds of updates, param changes and detach`() {
        val registry = DefaultScenes.registry()
        for (id in registry.ids) {
            val scene = registry.create(id)!!
            scene.onAttach(SceneParams())
            // Actualizar y dibujar sin tamaño no debe fallar (la superficie aún no ha medido).
            scene.update(loudFrame(), 1f / 60f)
            scene.draw(canvas)
            scene.onResize(1080, 2200)
            repeat(300) { i ->
                val frame = when (i % 4) {
                    0 -> loudFrame(onset = if (i % 20 == 0) 0.9f else 0f)
                    1 -> synthetic.ambient(1f / 60f, i * 16_000L)
                    2 -> synthetic.silent()
                    else -> synthetic.decay(loudFrame(), 1f / 60f)
                }
                scene.update(frame, if (i % 7 == 0) 0.1f else 1f / 60f)
                if (i % 3 == 0) scene.draw(canvas)
            }
            // Cambios de parámetros: densidad y calidad reconstruyen; paleta cambia pinceles.
            scene.onParamsChanged(SceneParams(density = 2f, quality = 1f, paletteId = "ember"))
            scene.update(loudFrame(0.8f), 1f / 60f)
            scene.draw(canvas)
            scene.onParamsChanged(SceneParams(density = 0.25f, quality = 0.3f, deviceScale = 0.6f, paletteId = "mono", intensity = 2f, speed = 3f))
            repeat(60) { scene.update(loudFrame(), 1f / 30f); scene.draw(canvas) }
            // Tamaños extremos: apaisado estrecho (1024x600 del autoestéreo incluido) y cuadrado pequeño.
            scene.onResize(1024, 600)
            repeat(30) { scene.update(loudFrame(), 1f / 30f); scene.draw(canvas) }
            scene.onResize(2400, 300)
            repeat(30) { scene.update(loudFrame(), 1f / 60f); scene.draw(canvas) }
            scene.onResize(64, 64)
            repeat(30) { scene.update(synthetic.ambient(1f / 60f, 0L), 1f / 60f); scene.draw(canvas) }
            scene.onDetach()
        }
    }

    @Test
    fun `scenes removed in 0_20 are no longer registered`() {
        val ids = DefaultScenes.registry().ids
        for (old in listOf("rings", "particles", "tunnel", "lines", "ambient")) assertThat(ids).doesNotContain(old)
    }

    @Test
    fun `led bars light green, amber and red segments and round levels to segments`() {
        assertThat(SpectrumBarsScene.segments(0f)).isEqualTo(0)
        assertThat(SpectrumBarsScene.segments(1f)).isEqualTo(SpectrumBarsScene.ROWS)
        assertThat(SpectrumBarsScene.segments(2f)).isEqualTo(SpectrumBarsScene.ROWS)
        assertThat(SpectrumBarsScene.segments(0.5f)).isEqualTo(4)
        val colors = (0 until SpectrumBarsScene.ROWS).map { SpectrumBarsScene.ledColor(it) }
        assertThat(colors.distinct()).hasSize(3)
        assertThat(colors.first()).isNotEqualTo(colors.last())
        assertThat(colors.takeLast(3).distinct()).hasSize(1)
    }

    @Test
    fun `led panel is painted once per size, not per quality or palette change`() {
        val scene = SpectrumBarsScene()
        scene.onAttach(SceneParams(deviceScale = 0.6f))
        scene.onResize(758, 395)
        assertThat(scene.panelBuilds).isEqualTo(1)
        for (q in floatArrayOf(0.75f, 0.56f, 0.9f, 1f, 0.42f)) scene.onParamsChanged(SceneParams(quality = q, deviceScale = 0.6f))
        scene.onParamsChanged(SceneParams(paletteId = "neon", deviceScale = 0.6f))
        assertThat(scene.panelBuilds).isEqualTo(1)
        scene.onResize(360, 360)
        assertThat(scene.panelBuilds).isEqualTo(2)
    }

    @Test
    fun `led bars follow the ten octave bands`() {
        val scene = SpectrumBarsScene()
        scene.onAttach(SceneParams())
        scene.onResize(1024, 534)
        val n = config.bandCount
        // Solo graves: la primera columna se llena y la última (16 kHz) queda apagada.
        val loud = loudFrame()
        val bassOnly = AudioFrame(
            spectrum = loud.spectrum, bands = FloatArray(n) { if (it < n / 8) 1f else 0f },
            bass = 1f, mid = 0f, treble = 0f, rms = 0.5f, peak = 1f, onset = 0f,
            waveformLeft = loud.waveformLeft, waveformRight = loud.waveformRight, timestampUs = 0L, synthetic = false,
        )
        repeat(60) { scene.update(bassOnly, 1f / 30f) }
        scene.draw(canvas)
        assertThat(SpectrumBarsScene.segments(scene.level(0))).isEqualTo(SpectrumBarsScene.ROWS)
        assertThat(SpectrumBarsScene.segments(scene.level(SpectrumBarsScene.COLUMNS - 1))).isEqualTo(0)
    }

    @Test
    fun `led bars rise fast and fall smoothly at a steady pace`() {
        val scene = SpectrumBarsScene()
        scene.onAttach(SceneParams())
        scene.onResize(1024, 534)
        val n = config.bandCount
        val loud = loudFrame()
        fun frameWith(value: Float) = AudioFrame(
            spectrum = loud.spectrum, bands = FloatArray(n) { value },
            bass = value, mid = value, treble = value, rms = value, peak = value, onset = 0f,
            waveformLeft = loud.waveformLeft, waveformRight = loud.waveformRight, timestampUs = 0L, synthetic = false,
        )
        val dt = 1f / 30f
        // Subida: en ~0,2 s la columna ya está casi llena.
        repeat(6) { scene.update(frameWith(1f), dt) }
        assertThat(scene.level(3)).isGreaterThan(0.8f)
        repeat(30) { scene.update(frameWith(1f), dt) }
        // Bajada: monótona, sin desplomarse de golpe, y vacía en algo más de un segundo.
        var previous = scene.level(3)
        val trace = mutableListOf<Float>()
        repeat(45) {
            scene.update(frameWith(0f), dt)
            val now = scene.level(3)
            assertThat(now).isAtMost(previous)
            assertThat(previous - now).isAtMost(SpectrumBarsScene.RELEASE_PER_SECOND * dt + 1e-4f)
            trace += now
            previous = now
        }
        assertThat(trace[8]).isGreaterThan(0.5f) // a los 0,3 s aún queda más de media columna
        assertThat(trace.last()).isLessThan(0.01f) // a los 1,5 s, vacía
        scene.draw(canvas)
    }

    @Test
    fun `terrain paints its background once per size and palette, not per quality change`() {
        val scene = TerrainScene()
        scene.onAttach(SceneParams(deviceScale = 0.6f))
        scene.onResize(758, 395)
        val built = scene.backgroundBuilds
        for (q in floatArrayOf(0.75f, 0.56f, 0.9f, 1f, 0.42f)) scene.onParamsChanged(SceneParams(quality = q, deviceScale = 0.6f))
        assertThat(scene.backgroundBuilds).isEqualTo(built)
        scene.onParamsChanged(SceneParams(paletteId = "neon", deviceScale = 0.6f))
        assertThat(scene.backgroundBuilds).isEqualTo(built + 1)
        scene.onResize(1024, 534)
        assertThat(scene.backgroundBuilds).isEqualTo(built + 2)
    }

    @Test
    fun `scenes tolerate tiny band counts`() {
        val tinyConfig = AnalyzerConfig(bandCount = 2)
        val tiny = SyntheticFrameSource(tinyConfig)
        val registry = DefaultScenes.registry()
        for (id in registry.ids) {
            val scene = registry.create(id)!!
            scene.onAttach(SceneParams(density = 2f))
            scene.onResize(800, 600)
            repeat(50) { scene.update(tiny.ambient(1f / 60f, 0L), 1f / 60f); scene.draw(canvas) }
            scene.onDetach()
        }
    }
}
