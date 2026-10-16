package com.radillo.playerwave.visualizer.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QualityGovernorTest {

    private val smooth = 1f / 60f
    private val janky = 1f / 20f // 50 ms: un vsync perdido y medio

    private fun QualityGovernor.feed(dt: Float, frames: Int): List<Float> {
        val changes = ArrayList<Float>()
        repeat(frames) { onFrame(dt)?.let(changes::add) }
        return changes
    }

    @Test
    fun `steady 60 fps keeps full quality`() {
        val governor = QualityGovernor()
        val changes = governor.feed(smooth, 3_000)
        assertThat(changes).isEmpty()
        assertThat(governor.quality).isEqualTo(1f)
    }

    @Test
    fun `sustained jank lowers quality step by step down to the floor`() {
        val governor = QualityGovernor(minQuality = 0.3f)
        val changes = governor.feed(janky, 4_000)
        assertThat(changes).isNotEmpty()
        // Cada paso multiplica por 0.75 y nunca baja del mínimo.
        var previous = 1f
        for (q in changes) {
            assertThat(q).isLessThan(previous)
            assertThat(q).isAtLeast(0.3f)
            previous = q
        }
        assertThat(governor.quality).isWithin(1e-6f).of(0.3f)
    }

    @Test
    fun `quality recovers gradually after the device catches up`() {
        val governor = QualityGovernor(minQuality = 0.3f)
        governor.feed(janky, 4_000)
        assertThat(governor.quality).isWithin(1e-6f).of(0.3f)

        val changes = governor.feed(smooth, 8_000)
        assertThat(changes).isNotEmpty()
        var previous = 0.3f
        for (q in changes) {
            assertThat(q).isGreaterThan(previous)
            previous = q
        }
        assertThat(governor.quality).isEqualTo(1f)
    }

    @Test
    fun `warm up after reset ignores the first slow frames`() {
        val governor = QualityGovernor(warmUpSeconds = 1.5f, windowFrames = 60)
        governor.reset()
        // 1,5 s de frames lentos (30 × 50 ms) durante la gracia: no deben contar.
        assertThat(governor.feed(janky, 30)).isEmpty()
        // Después, una ventana completa de frames rápidos tampoco cambia nada.
        assertThat(governor.feed(smooth, 60)).isEmpty()
        assertThat(governor.quality).isEqualTo(1f)
    }

    @Test
    fun `a long pause is not counted as a late frame`() {
        val governor = QualityGovernor(warmUpSeconds = 0f)
        governor.feed(smooth, 100)
        repeat(50) {
            governor.onFrame(2f) // vuelta del segundo plano
            governor.feed(smooth, 70)
        }
        assertThat(governor.quality).isEqualTo(1f)
    }

    @Test
    fun `a few late frames per window are tolerated`() {
        val governor = QualityGovernor(warmUpSeconds = 0f, windowFrames = 60)
        repeat(40) {
            governor.feed(smooth, 55)
            governor.feed(janky, 5) // 8 % tardíos < 20 %
        }
        assertThat(governor.quality).isEqualTo(1f)
    }

    @Test
    fun `disabled governor never changes quality and resets to full`() {
        val governor = QualityGovernor()
        governor.feed(janky, 4_000)
        assertThat(governor.quality).isLessThan(1f)
        governor.enabled = false
        assertThat(governor.quality).isEqualTo(1f)
        assertThat(governor.feed(janky, 4_000)).isEmpty()
        assertThat(governor.quality).isEqualTo(1f)
    }

    @Test
    fun `a frame cap raises the late threshold so capped frames are not jank`() {
        val governor = QualityGovernor(warmUpSeconds = 0f)
        governor.targetFrameSeconds = 1f / 30f
        // Con tope de 30 fps cada frame dura ~33 ms: no es tardío (umbral 53 ms).
        assertThat(governor.feed(1f / 30f, 3_000)).isEmpty()
        assertThat(governor.quality).isEqualTo(1f)
        // Pero perder frames incluso con el tope (70 ms) sí baja la calidad.
        governor.feed(0.07f, 600)
        assertThat(governor.quality).isLessThan(1f)
    }

    @Test
    fun `min quality can be lowered for low end devices`() {
        val governor = QualityGovernor(warmUpSeconds = 0f)
        governor.minQuality = 0.25f
        governor.feed(janky, 4_000)
        assertThat(governor.quality).isWithin(1e-6f).of(0.25f)
        governor.minQuality = 0.5f // subirlo eleva la calidad vigente al nuevo mínimo
        assertThat(governor.quality).isWithin(1e-6f).of(0.5f)
    }

    @Test
    fun `high refresh rate does not count 60 fps as jank`() {
        val governor = QualityGovernor(warmUpSeconds = 0f)
        // Pantalla de 120 Hz que a veces entrega a 60: 16,7 ms < 22 ms, no es tardío.
        repeat(30) {
            governor.feed(1f / 120f, 30)
            governor.feed(1f / 60f, 30)
        }
        assertThat(governor.quality).isEqualTo(1f)
    }
}
