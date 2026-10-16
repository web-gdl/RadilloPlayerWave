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
    fun `registry exposes the seven phase-4 scenes with unique ids and canvas backend`() {
        val registry = DefaultScenes.registry()
        assertThat(registry.ids).containsExactly(
            SpectrumBarsScene.ID, OscilloscopeScene.ID, RingsScene.ID,
            ParticlesScene.ID, TunnelScene.ID, LineFieldScene.ID, AmbientScene.ID,
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
