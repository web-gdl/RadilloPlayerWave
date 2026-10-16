package com.radillo.playerwave.visualizer.analysis

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BandMapperTest {

    @Test
    fun `las bandas son contiguas, crecientes y cubren al menos un bin`() {
        val mapper = BandMapper(sampleRate = 48_000, fftSize = 2048, bandCount = 64, minHz = 30f, maxHz = 16_000f)
        for (i in 0 until mapper.bandCount) {
            assertThat(mapper.ends[i]).isGreaterThan(mapper.starts[i])
            if (i > 0) assertThat(mapper.starts[i]).isAtLeast(mapper.ends[i - 1])
            assertThat(mapper.ends[i]).isAtMost(1024)
        }
        assertThat(mapper.centerHz.first()).isLessThan(100f)
        assertThat(mapper.centerHz.last()).isGreaterThan(10_000f)
    }

    @Test
    fun `apply promedia las magnitudes de cada banda`() {
        val mapper = BandMapper(sampleRate = 48_000, fftSize = 2048, bandCount = 16, minHz = 30f, maxHz = 16_000f)
        val mags = FloatArray(1024) { 2f }
        val out = FloatArray(16)
        mapper.apply(mags, out)
        for (v in out) assertThat(v).isWithin(1e-6f).of(2f)
    }

    @Test
    fun `bandIndexFor localiza la primera banda por encima de una frecuencia`() {
        val mapper = BandMapper(sampleRate = 44_100, fftSize = 2048, bandCount = 32, minHz = 30f, maxHz = 16_000f)
        val bass = mapper.bandIndexFor(250f)
        val mid = mapper.bandIndexFor(4_000f)
        assertThat(bass).isGreaterThan(0)
        assertThat(mid).isGreaterThan(bass)
        assertThat(mid).isLessThan(32)
        assertThat(mapper.centerHz[bass]).isAtLeast(250f)
    }
}
