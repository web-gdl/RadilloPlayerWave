package com.radillo.playerwave.core.device

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeviceProfileTest {

    private fun profile(
        totalRamMb: Long = 3_800,
        isLowRamDevice: Boolean = false,
        maxHeapMb: Long = 512,
        is64Bit: Boolean = true,
        declaredApi: Int = 34,
        realApi: Int = 34,
        widthPx: Int = 1080,
        heightPx: Int = 2400,
        hasAccelerometer: Boolean = true,
        isCarUiMode: Boolean = false,
        hardwareHints: List<String> = emptyList(),
    ) = DeviceProfile(
        totalRamMb, isLowRamDevice, maxHeapMb, is64Bit, declaredApi, realApi,
        widthPx, heightPx, 420, hasAccelerometer, isCarUiMode, hardwareHints,
    )

    @Test
    fun `modern phone is not low end`() {
        val p = profile()
        assertThat(p.lowEnd).isFalse()
        assertThat(p.reasons).isEmpty()
        assertThat(p.defaultMaxFps).isEqualTo(60)
        assertThat(p.visualizerDensityScale).isEqualTo(1f)
        assertThat(p.fixedLandscape).isFalse()
        assertThat(p.apiSpoofed).isFalse()
    }

    @Test
    fun `mtk 8227L head unit is low end, landscape and detected as spoofed`() {
        val p = profile(
            totalRamMb = 983,
            maxHeapMb = 192,
            is64Bit = false,
            declaredApi = 30,
            realApi = 27,
            widthPx = 1024,
            heightPx = 600,
            hasAccelerometer = false,
            hardwareHints = listOf("8227"),
        )
        assertThat(p.lowEnd).isTrue()
        assertThat(p.reasons).hasSize(4) // RAM, heap, 32 bits, hardware
        assertThat(p.defaultMaxFps).isEqualTo(30)
        assertThat(p.visualizerDensityScale).isWithin(1e-6f).of(0.6f)
        assertThat(p.minQuality).isWithin(1e-6f).of(0.25f)
        assertThat(p.artworkMaxPx).isEqualTo(256)
        assertThat(p.fixedLandscape).isTrue()
        assertThat(p.apiSpoofed).isTrue()
    }

    @Test
    fun `a single signal is enough to be low end`() {
        assertThat(profile(isLowRamDevice = true).lowEnd).isTrue()
        assertThat(profile(is64Bit = false).lowEnd).isTrue()
        assertThat(profile(totalRamMb = 1_500).lowEnd).isTrue()
        assertThat(profile(totalRamMb = 0).lowEnd).isFalse() // RAM desconocida no cuenta
    }

    @Test
    fun `car ui mode fixes landscape even with an accelerometer`() {
        assertThat(profile(isCarUiMode = true).fixedLandscape).isTrue()
        // Un teléfono sin acelerómetro en vertical no se fuerza a horizontal.
        assertThat(profile(hasAccelerometer = false, widthPx = 720, heightPx = 1280).fixedLandscape).isFalse()
    }
}
