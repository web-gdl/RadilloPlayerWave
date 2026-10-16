package com.radillo.playerwave.visualizer.analysis

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class FftTest {

    @Test
    fun `un seno puro concentra la energia en su bin`() {
        val n = 1024
        val bin = 37
        val fft = Fft(n)
        val window = Fft.hannWindow(n)
        val re = FloatArray(n) { i -> (sin(2.0 * PI * bin * i / n)).toFloat() * window[i] }
        val im = FloatArray(n)
        fft.transform(re, im)
        val mags = FloatArray(n / 2)
        fft.magnitudes(re, im, mags, scale = 4f / n)

        var argmax = 0
        for (k in mags.indices) if (mags[k] > mags[argmax]) argmax = k
        assertThat(argmax).isEqualTo(bin)
        // Con Hann, el pico normalizado es ~1 (amplitud del seno) y los bins alejados ~0.
        assertThat(mags[bin]).isWithin(0.08f).of(1f)
        assertThat(mags[bin + 10]).isLessThan(0.02f)
        assertThat(mags[3]).isLessThan(0.02f)
    }

    @Test
    fun `un impulso produce un espectro plano`() {
        val n = 256
        val fft = Fft(n)
        val re = FloatArray(n).also { it[0] = 1f }
        val im = FloatArray(n)
        fft.transform(re, im)
        for (k in 0 until n) {
            assertThat(re[k]).isWithin(1e-5f).of(1f)
            assertThat(im[k]).isWithin(1e-5f).of(0f)
        }
    }

    @Test
    fun `se cumple la identidad de Parseval`() {
        val n = 512
        val fft = Fft(n)
        val rnd = java.util.Random(7)
        val x = FloatArray(n) { (rnd.nextFloat() * 2f - 1f) }
        val re = x.copyOf()
        val im = FloatArray(n)
        fft.transform(re, im)
        var timeEnergy = 0.0
        for (v in x) timeEnergy += (v * v).toDouble()
        var freqEnergy = 0.0
        for (k in 0 until n) freqEnergy += (re[k] * re[k] + im[k] * im[k]).toDouble()
        freqEnergy /= n
        assertThat(freqEnergy).isWithin(timeEnergy * 1e-3).of(timeEnergy)
    }

    @Test
    fun `rechaza tamanos que no son potencia de dos`() {
        try {
            Fft(1000)
            throw AssertionError("Debió lanzar IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // esperado
        }
    }
}
