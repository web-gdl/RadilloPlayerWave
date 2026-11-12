package com.radillo.playerwave.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import com.google.common.truth.Truth.assertThat
import com.radillo.playerwave.visualizer.analysis.AnalyzerConfig
import com.radillo.playerwave.visualizer.analysis.AudioFrame
import com.radillo.playerwave.visualizer.engine.DefaultScenes
import com.radillo.playerwave.visualizer.engine.Scene
import com.radillo.playerwave.visualizer.engine.SceneParams
import com.radillo.playerwave.visualizer.render.RenderSize
import com.radillo.playerwave.visualizer.scenes.SpectrumBarsScene
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

/**
 * Coste de dibujar un frame **con la CPU** (lienzo de mapa de bits, el mismo motor de dibujo que usa
 * el autoestéreo sin GPU), medido con el Skia real de Robolectric. Los milisegundos de una máquina
 * de CI no son los del equipo, pero las **proporciones** sí se trasladan: cuánto ahorra dibujar la
 * pantalla completa con el presupuesto de píxeles ([RenderSize]) y cuánto ahorra cada bajada de
 * calidad en la escena de barras.
 *
 * Deja la tabla en `build/outputs/screenshots/visualizer_cost.txt` y comprueba las proporciones
 * con margen amplio (para no fallar por ruido de la máquina).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class VisualizerCostTest {

    private val config = AnalyzerConfig()
    private val random = Random(7)

    private fun frame() = AudioFrame(
        spectrum = FloatArray(config.spectrumSize) { random.nextFloat() * 0.5f },
        bands = FloatArray(config.bandCount) { 0.35f + random.nextFloat() * 0.6f },
        bass = 0.8f, mid = 0.5f, treble = 0.6f, rms = 0.7f, peak = 0.95f, onset = 0.4f,
        waveformLeft = FloatArray(config.waveformSize) { (random.nextFloat() - 0.5f) * 1.6f },
        waveformRight = FloatArray(config.waveformSize) { (random.nextFloat() - 0.5f) * 1.6f },
        timestampUs = 0L, synthetic = false,
    )

    /** Milisegundos por frame (mediana de varias tandas) de [scene] a [w]×[h] con [quality]. */
    private fun cost(scene: Scene, w: Int, h: Int, quality: Float): Double {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Como en el autoestéreo: densidad del perfil de gama baja (0,6) y la calidad del gobernador.
        scene.onAttach(SceneParams(quality = quality, deviceScale = 0.6f))
        scene.onResize(w, h)
        val frames = List(8) { frame() }
        repeat(20) { scene.update(frames[it % frames.size], 1f / 30f); scene.draw(canvas) } // calentar
        val rounds = DoubleArray(5) {
            val start = System.nanoTime()
            repeat(FRAMES) { i ->
                scene.update(frames[i % frames.size], 1f / 30f)
                canvas.drawColor(0xFF05070D.toInt())
                scene.draw(canvas)
            }
            (System.nanoTime() - start) / 1e6 / FRAMES
        }
        bitmap.recycle()
        return rounds.sorted()[rounds.size / 2]
    }

    @Test
    fun `full screen costs less with the pixel budget and with each quality step`() {
        val full = 1024 to 600
        val packed = RenderSize.fit(full.first, full.second, 300_000)
        val reduced = RenderSize.widthOf(packed) to RenderSize.heightOf(packed)
        val art = 360 to 360
        val out = StringBuilder("Coste por frame dibujando con CPU (ms, máquina de CI; valen las proporciones)\n")
        out.append("escena;carátula 360x360;completa ${full.first}x${full.second};reducida ${reduced.first}x${reduced.second};ahorro\n")

        val registry = DefaultScenes.registry()
        var barsFull = 0.0
        var barsReduced = 0.0
        for (id in registry.ids) {
            val a = cost(registry.create(id)!!, art.first, art.second, 1f)
            val f = cost(registry.create(id)!!, full.first, full.second, 1f)
            val r = cost(registry.create(id)!!, reduced.first, reduced.second, 1f)
            out.append("%s;%.2f;%.2f;%.2f;%.0f %%\n".format(id, a, f, r, (1 - r / f) * 100))
            if (id == SpectrumBarsScene.ID) { barsFull = f; barsReduced = r }
        }

        out.append("\nBarras a pantalla completa reducida, según la calidad del gobernador\ncalidad;ms\n")
        val byQuality = listOf(1f, 0.75f, 0.5f, 0.25f).map { q ->
            q to cost(SpectrumBarsScene(), reduced.first, reduced.second, q).also { out.append("%.2f;%.2f\n".format(q, it)) }
        }
        File("build/outputs/screenshots").apply { mkdirs() }.resolve("visualizer_cost.txt").writeText(out.toString())
        println(out)

        // Con la mitad de píxeles, las barras cuestan claramente menos (margen amplio por el ruido).
        assertThat(barsReduced).isLessThan(barsFull * 0.85)
        // Y bajar la calidad abarata de verdad: al mínimo, menos de dos tercios que al máximo.
        assertThat(byQuality.last().second).isLessThan(byQuality.first().second * 0.67)
    }

    private companion object {
        const val FRAMES = 40
    }
}
