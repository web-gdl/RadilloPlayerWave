package com.radillo.playerwave.visualizer.engine

import android.graphics.Canvas
import com.google.common.truth.Truth.assertThat
import com.radillo.playerwave.visualizer.analysis.AudioSource
import com.radillo.playerwave.visualizer.analysis.AudioSourceStatus
import com.radillo.playerwave.visualizer.analysis.PcmBlock
import com.radillo.playerwave.visualizer.analysis.PlaybackClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Regresión de la Fase B: el reloj se consultaba desde el hilo de render y lanzaba en cada frame,
 * así que no se dibujaba nada. El motor debe seguir dibujando aunque el reloj falle.
 */
class VisualizerEngineRenderTest {

    private class SilentSource : AudioSource {
        override val blocks: Flow<PcmBlock> = emptyFlow()
        override val status: StateFlow<AudioSourceStatus> = MutableStateFlow(AudioSourceStatus.IDLE)
    }

    private class CountingScene : Scene {
        override val id = "counting"
        override val displayName = "counting"
        override val backend = RenderBackend.CANVAS
        var updates = 0
        var draws = 0
        override fun onAttach(params: SceneParams) = Unit
        override fun onParamsChanged(params: SceneParams) = Unit
        override fun onResize(widthPx: Int, heightPx: Int) = Unit
        override fun update(frame: com.radillo.playerwave.visualizer.analysis.AudioFrame, deltaSeconds: Float) { updates++ }
        override fun onDetach() = Unit
        override fun draw(canvas: Canvas) { draws++ }
    }

    @Test
    fun `engine keeps drawing when the playback clock throws`() {
        val scene = CountingScene()
        val throwingClock = object : PlaybackClock {
            override fun positionUs(): Long? = throw IllegalStateException("MediaController method is called from a wrong thread")
            override fun isPlaying(): Boolean = throw IllegalStateException("MediaController method is called from a wrong thread")
        }
        val engine = VisualizerEngine(SilentSource(), throwingClock, SceneRegistry(listOf("counting" to SceneFactory { scene })))
        engine.setScene("counting")
        engine.onResize(1024, 600)
        val canvas = Canvas()
        var now = 1_000_000_000L
        repeat(30) {
            engine.render(canvas, now)
            now += 33_333_333L
        }
        assertThat(scene.updates).isEqualTo(30)
        assertThat(scene.draws).isEqualTo(30)
    }

    /**
     * Regresión del cierre al pasar de «Visualizador» a «Reproduciendo»: una superficie se destruye
     * y otra se crea casi a la vez, y el análisis anterior seguía vivo mientras arrancaba el nuevo,
     * con dos hilos sobre el mismo analizador. El nuevo debe esperar a que el anterior termine.
     */
    @Test
    fun `restarting the analysis never runs two collectors at once`() {
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        val starts = AtomicInteger(0)
        val slowSource = object : AudioSource {
            override val status: StateFlow<AudioSourceStatus> = MutableStateFlow(AudioSourceStatus.ACTIVE)
            override val blocks: Flow<PcmBlock> = flow {
                val now = active.incrementAndGet()
                starts.incrementAndGet()
                maxActive.accumulateAndGet(now) { a, b -> maxOf(a, b) }
                try {
                    awaitCancellation()
                } finally {
                    Thread.sleep(150) // cierre lento, como en un A7 ocupado
                    active.decrementAndGet()
                }
            }
        }
        val clock = object : PlaybackClock {
            override fun positionUs(): Long? = null
            override fun isPlaying(): Boolean = false
        }
        val engine = VisualizerEngine(slowSource, clock, DefaultScenes.registry())
        repeat(3) {
            engine.attach()
            Thread.sleep(40)
            engine.detach()
        }
        engine.attach()
        val deadline = System.currentTimeMillis() + 3_000
        while (starts.get() < 2 && System.currentTimeMillis() < deadline) Thread.sleep(20)
        Thread.sleep(400)
        assertThat(starts.get()).isAtLeast(2)
        assertThat(maxActive.get()).isEqualTo(1)
        engine.detach()
    }
}
