package com.radillo.playerwave.visualizer.analysis

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PcmRingBufferTest {

    private fun frames(from: Int, count: Int): FloatArray =
        FloatArray(count * 2) { i -> (from + i / 2).toFloat() + (if (i % 2 == 0) 0f else 0.5f) }

    @Test
    fun `lectura secuencial con vuelta al inicio`() {
        val ring = PcmRingBuffer(capacityFrames = 100)
        ring.write(frames(0, 70), 0, 70)
        val dst = FloatArray(200)
        assertThat(ring.read(dst, 50)).isEqualTo(50)
        assertThat(dst[0]).isEqualTo(0f)
        assertThat(dst[1]).isEqualTo(0.5f)
        assertThat(dst[98]).isEqualTo(49f)

        ring.write(frames(70, 60), 0, 60) // cruza el final del anillo
        assertThat(ring.available()).isEqualTo(80)
        assertThat(ring.read(dst, 80)).isEqualTo(80)
        assertThat(dst[0]).isEqualTo(50f)
        assertThat(dst[158]).isEqualTo(129f)
        assertThat(ring.read(dst, 10)).isEqualTo(0)
    }

    @Test
    fun `al desbordar se conservan los frames mas recientes`() {
        val ring = PcmRingBuffer(capacityFrames = 64)
        ring.write(frames(0, 100), 0, 100)
        assertThat(ring.available()).isEqualTo(64)
        val dst = FloatArray(128)
        assertThat(ring.read(dst, 64)).isEqualTo(64)
        assertThat(dst[0]).isEqualTo(36f)
        assertThat(dst[126]).isEqualTo(99f)
    }

    @Test
    fun `las marcas temporales se derivan de la referencia`() {
        val ring = PcmRingBuffer(capacityFrames = 1000)
        ring.write(frames(0, 10), 0, 10)
        ring.markBase(positionUs = 5_000_000L, sampleRate = 48_000)
        ring.write(frames(10, 480), 0, 480)
        val dst = FloatArray(2000)
        ring.read(dst, 10) // los 10 anteriores a la referencia
        ring.read(dst, 480)
        assertThat(ring.timestampUs(ring.lastReadStartFrame)).isEqualTo(5_000_000L)
        assertThat(ring.timestampUs(ring.lastReadStartFrame + 480)).isEqualTo(5_010_000L)
    }
}
