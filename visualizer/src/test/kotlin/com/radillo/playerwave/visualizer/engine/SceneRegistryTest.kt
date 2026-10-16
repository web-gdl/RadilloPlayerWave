package com.radillo.playerwave.visualizer.engine

import android.graphics.Canvas
import com.google.common.truth.Truth.assertThat
import com.radillo.playerwave.visualizer.analysis.AudioFrame
import org.junit.Test

class SceneRegistryTest {

    private class FakeScene(override val id: String) : Scene {
        override val displayName: String = id
        override val backend: RenderBackend = RenderBackend.CANVAS
        var attached = false
        override fun onAttach(params: SceneParams) { attached = true }
        override fun onParamsChanged(params: SceneParams) = Unit
        override fun onResize(widthPx: Int, heightPx: Int) = Unit
        override fun update(frame: AudioFrame, deltaSeconds: Float) = Unit
        override fun onDetach() { attached = false }
        override fun draw(canvas: Canvas) = Unit
    }

    @Test
    fun `register keeps insertion order and creates scenes lazily`() {
        var created = 0
        val registry = SceneRegistry()
        registry.register("b") { created++; FakeScene("b") }
        registry.register("a") { created++; FakeScene("a") }

        assertThat(registry.ids).containsExactly("b", "a").inOrder()
        assertThat(created).isEqualTo(0)

        val scene = registry.create("a")
        assertThat(scene).isNotNull()
        assertThat(scene!!.id).isEqualTo("a")
        assertThat(created).isEqualTo(1)
    }

    @Test
    fun `unknown id returns null`() {
        assertThat(SceneRegistry().create("missing")).isNull()
    }

    @Test
    fun `silent frame has requested sizes and is synthetic`() {
        val frame = AudioFrame.silent(spectrumSize = 1024, bandCount = 32, waveformSize = 512)
        assertThat(frame.spectrum).hasLength(1024)
        assertThat(frame.bands).hasLength(32)
        assertThat(frame.waveformLeft).hasLength(512)
        assertThat(frame.synthetic).isTrue()
    }
}
