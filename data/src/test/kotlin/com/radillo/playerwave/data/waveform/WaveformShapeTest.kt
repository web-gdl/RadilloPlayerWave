package com.radillo.playerwave.data.waveform

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WaveformShapeTest {

    @Test
    fun `el tramo mas fuerte llega al tope y los suaves no desaparecen`() {
        val peaks = WaveformShape.normalize(floatArrayOf(0.5f, 0.02f, 0f, 0.125f))!!
        assertThat(WaveformShape.level(peaks, 0)).isEqualTo(1f)
        // 4 % del máximo se ve como el 20 % de la altura (raíz cuadrada), no como una raya.
        assertThat(WaveformShape.level(peaks, 1)).isWithin(0.01f).of(0.2f)
        assertThat(WaveformShape.level(peaks, 2)).isEqualTo(0f)
        assertThat(WaveformShape.level(peaks, 3)).isWithin(0.01f).of(0.5f)
    }

    @Test
    fun `sin senal no hay onda`() {
        assertThat(WaveformShape.normalize(FloatArray(8))).isNull()
        assertThat(WaveformShape.normalize(FloatArray(0))).isNull()
        assertThat(WaveformShape.normalize(floatArrayOf(Float.NaN, Float.NaN))).isNull()
    }
}
