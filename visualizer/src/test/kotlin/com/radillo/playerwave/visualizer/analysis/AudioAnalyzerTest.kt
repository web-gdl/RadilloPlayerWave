package com.radillo.playerwave.visualizer.analysis

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AudioAnalyzerTest {

    private fun sineBlocks(hz: Double, seconds: Double, sampleRate: Int = 48_000, blockFrames: Int = 1024, amplitude: Float = 0.5f): List<PcmBlock> {
        val total = (seconds * sampleRate).toInt()
        val blocks = ArrayList<PcmBlock>()
        var frame = 0
        while (frame < total) {
            val count = minOf(blockFrames, total - frame)
            val samples = FloatArray(count * 2)
            for (i in 0 until count) {
                val v = (amplitude * sin(2.0 * PI * hz * (frame + i) / sampleRate)).toFloat()
                samples[i * 2] = v
                samples[i * 2 + 1] = v
            }
            blocks += PcmBlock(samples, 2, sampleRate, frame.toLong() * 1_000_000L / sampleRate)
            frame += count
        }
        return blocks
    }

    @Test
    fun `emite un frame por hop y clasifica un tono grave en las bandas bajas`() {
        val analyzer = AudioAnalyzer(AnalyzerConfig(framesPerSecond = 60))
        val frames = ArrayList<AudioFrame>()
        sineBlocks(hz = 80.0, seconds = 1.0).forEach { analyzer.push(it) { f -> frames += f } }

        assertThat(frames.size).isWithin(2).of(60)
        val last = frames.last()
        assertThat(last.synthetic).isFalse()
        assertThat(last.bands).hasLength(64)
        assertThat(last.spectrum).hasLength(1024)
        assertThat(last.waveformLeft).hasLength(512)
        assertThat(last.bass).isGreaterThan(last.treble)
        assertThat(last.bass).isGreaterThan(0.3f)
        var argmax = 0
        for (i in last.bands.indices) if (last.bands[i] > last.bands[argmax]) argmax = i
        assertThat(argmax).isLessThan(16)
        assertThat(last.rms).isGreaterThan(0.2f)
        assertThat(last.timestampUs).isGreaterThan(frames.first().timestampUs)
        // La forma de onda refleja la amplitud del seno.
        assertThat(last.waveformLeft.maxOf { kotlin.math.abs(it) }).isWithin(0.1f).of(0.5f)
    }

    @Test
    fun `un tono agudo activa las bandas altas`() {
        val analyzer = AudioAnalyzer()
        var last: AudioFrame? = null
        sineBlocks(hz = 8_000.0, seconds = 0.5).forEach { analyzer.push(it) { f -> last = f } }
        val frame = checkNotNull(last)
        assertThat(frame.treble).isGreaterThan(frame.bass)
    }

    @Test
    fun `el silencio produce niveles cercanos a cero`() {
        val analyzer = AudioAnalyzer()
        var last: AudioFrame? = null
        val silence = PcmBlock(FloatArray(48_000 * 2), 2, 48_000, 0L)
        analyzer.push(silence) { f -> last = f }
        val frame = checkNotNull(last)
        assertThat(frame.rms).isWithin(1e-4f).of(0f)
        assertThat(frame.bass).isLessThan(0.05f)
        assertThat(frame.onset).isWithin(1e-6f).of(0f)
    }
}
