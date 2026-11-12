package com.radillo.playerwave.visualizer.render

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RenderSizeTest {

    private fun fit(w: Int, h: Int, max: Int) = RenderSize.fit(w, h, max).let { RenderSize.widthOf(it) to RenderSize.heightOf(it) }

    @Test
    fun `small surfaces and no budget keep their size`() {
        assertThat(fit(360, 360, 300_000)).isEqualTo(360 to 360) // recuadro de la carátula
        assertThat(fit(1024, 600, 0)).isEqualTo(1024 to 600)     // sin tope (GPU o equipo capaz)
        assertThat(fit(0, 600, 300_000)).isEqualTo(0 to 0)
    }

    @Test
    fun `full screen on the car stereo is drawn with about half the pixels`() {
        val (w, h) = fit(1024, 600, 300_000)
        assertThat(w.toLong() * h).isAtMost(300_000L)
        assertThat(w.toLong() * h).isAtLeast(280_000L) // sin pasarse de recorte
        // Misma proporción (± redondeo a pares) y tamaños pares.
        assertThat(w.toFloat() / h).isWithin(0.02f).of(1024f / 600f)
        assertThat(w % 2).isEqualTo(0)
        assertThat(h % 2).isEqualTo(0)
    }

    @Test
    fun `the pause between frames grows with the frame time within limits`() {
        assertThat(RenderSize.yieldNanos(5_000_000L)).isEqualTo(RenderSize.MIN_YIELD_NANOS)
        assertThat(RenderSize.yieldNanos(80_000_000L)).isEqualTo(10_000_000L)
        assertThat(RenderSize.yieldNanos(900_000_000L)).isEqualTo(RenderSize.MAX_YIELD_NANOS)
    }
}
