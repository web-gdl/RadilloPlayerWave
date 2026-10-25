package com.radillo.playerwave.visualizer.analysis

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class SyntheticFramesTest {

    private val config = AnalyzerConfig(fftSize = 64, bandCount = 8, waveformSize = 16)

    private fun liveFrame(): AudioFrame = AudioFrame(
        spectrum = FloatArray(config.spectrumSize) { 0.5f + it * 0.01f },
        bands = FloatArray(config.bandCount) { 0.8f - it * 0.05f },
        bass = 0.7f, mid = 0.5f, treble = 0.3f, rms = 0.6f, peak = 0.9f, onset = 1f,
        waveformLeft = FloatArray(config.waveformSize) { it * 0.05f - 0.4f },
        waveformRight = FloatArray(config.waveformSize) { 0.4f - it * 0.05f },
        timestampUs = 1234L, synthetic = false,
    )

    @Test
    fun `decay escala el frame anterior sin modificarlo y reutiliza los buferes`() {
        val source = SyntheticFrameSource(config)
        val live = liveFrame()
        val original = liveFrame()
        val k = exp(-(1f / 60f) / 0.18f)
        var previous = live
        val outputs = ArrayList<AudioFrame>()
        repeat(6) {
            val next = source.decay(previous, 1f / 60f)
            for (i in next.bands.indices) assertThat(next.bands[i]).isEqualTo(previous.bands[i] * k)
            for (i in next.waveformRight.indices) assertThat(next.waveformRight[i]).isEqualTo(previous.waveformRight[i] * k)
            assertThat(next.rms).isEqualTo(previous.rms * k)
            assertThat(next.onset).isEqualTo(0f)
            assertThat(next.synthetic).isTrue()
            assertThat(next.timestampUs).isEqualTo(1234L)
            // El frame anterior no se toca mientras se calcula el siguiente.
            assertThat(next.spectrum === previous.spectrum).isFalse()
            outputs += next
            previous = next
        }
        // El frame en vivo (que vive en el historial) nunca se modifica.
        for (i in live.spectrum.indices) assertThat(live.spectrum[i]).isEqualTo(original.spectrum[i])
        for (i in live.waveformLeft.indices) assertThat(live.waveformLeft[i]).isEqualTo(original.waveformLeft[i])
        // Solo dos juegos de búferes alternados.
        assertThat(outputs[0].spectrum === outputs[2].spectrum).isTrue()
        assertThat(outputs[1].bands === outputs[3].bands).isTrue()
    }

    @Test
    fun `ambient coincide con la formula original y decay respeta L y R compartidos`() {
        val source = SyntheticFrameSource(config)
        var phase = 0f
        repeat(5) { step ->
            val dt = 1f / 60f + step * 0.003f
            val frame = source.ambient(dt, 77L)
            phase += dt * 0.6f
            for (k in 0 until config.spectrumSize) {
                val x = k.toFloat() / config.spectrumSize
                val expected = 0.15f * exp(-x * 6f) * (0.6f + 0.4f * sin((phase * 3f + x * 40f).toFloat()))
                assertThat(frame.spectrum[k]).isEqualTo(expected)
            }
            for (i in 0 until config.waveformSize) {
                val t = i.toFloat() / config.waveformSize
                val expected = 0.25f * sin((t * 2f * PI * 2f + phase * 4f).toFloat()) * sin((phase * 0.7f).toFloat())
                assertThat(frame.waveformLeft[i]).isEqualTo(expected)
                assertThat(frame.waveformRight[i]).isEqualTo(expected)
            }
            for (i in 0 until config.bandCount) {
                val x = i.toFloat() / config.bandCount
                val wave = 0.5f + 0.5f * sin((phase * 2f * PI + x * 7f).toFloat())
                assertThat(frame.bands[i]).isEqualTo((0.18f + 0.22f * wave * (1f - x * 0.6f)).coerceIn(0f, 1f))
            }
            assertThat(frame.timestampUs).isEqualTo(77L)
        }
        val ambient = source.ambient(1f / 60f, 0L)
        val snapshot = ambient.waveformLeft.copyOf()
        val decayed = source.decay(ambient, 1f / 60f)
        val k = exp(-(1f / 60f) / 0.18f)
        for (i in snapshot.indices) {
            assertThat(decayed.waveformLeft[i]).isEqualTo(snapshot[i] * k)
            assertThat(decayed.waveformRight[i]).isEqualTo(snapshot[i] * k)
        }
    }
}
