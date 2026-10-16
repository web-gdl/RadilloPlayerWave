package com.radillo.playerwave.visualizer.analysis

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FrameHistoryTest {

    private fun frame(timestampUs: Long) = AudioFrame(
        spectrum = FloatArray(4), bands = FloatArray(4), bass = 0f, mid = 0f, treble = 0f, rms = 0f, peak = 0f,
        onset = 0f, waveformLeft = FloatArray(4), waveformRight = FloatArray(4), timestampUs = timestampUs, synthetic = false,
    )

    @Test
    fun `selecciona por marca temporal cuando coincide`() {
        val history = FrameHistory(capacity = 8)
        for (i in 0 until 8) history.add(frame(i * 16_000L), arrivalNanos = i * 16_000_000L)
        val sel = checkNotNull(history.select(positionUs = 50_000L, nowNanos = 200_000_000L, latencyNanos = 0L))
        assertThat(sel.matchedByTimestamp).isTrue()
        assertThat(sel.entry.frame.timestampUs).isEqualTo(48_000L)
    }

    @Test
    fun `sin coincidencia temporal usa el instante de llegada y la latencia`() {
        val history = FrameHistory(capacity = 8)
        for (i in 0 until 8) history.add(frame(10_000_000L + i * 16_000L), arrivalNanos = i * 16_000_000L)
        // Posición incompatible (otra pista): se cae a la llegada.
        val sel = checkNotNull(history.select(positionUs = 500_000L, nowNanos = 112_000_000L, latencyNanos = 32_000_000L))
        assertThat(sel.matchedByTimestamp).isFalse()
        assertThat(sel.entry.arrivalNanos).isEqualTo(80_000_000L)
    }

    @Test
    fun `sin posicion devuelve el mas cercano a ahora menos latencia y respeta la capacidad`() {
        val history = FrameHistory(capacity = 4)
        for (i in 0 until 10) history.add(frame(i.toLong()), arrivalNanos = i * 1_000L)
        assertThat(history.latest()!!.frame.timestampUs).isEqualTo(9L)
        val sel = checkNotNull(history.select(null, nowNanos = 9_000L, latencyNanos = 2_000L))
        assertThat(sel.entry.frame.timestampUs).isEqualTo(7L)
        assertThat(history.select(null, 0L, 0L)!!.entry.frame.timestampUs).isEqualTo(6L)
    }
}
