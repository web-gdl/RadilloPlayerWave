package com.radillo.playerwave.ui

import androidx.core.graphics.ColorUtils
import com.google.common.truth.Truth.assertThat
import com.radillo.playerwave.ui.common.AccentColors
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** El acento que sale de la portada debe verse siempre sobre el fondo, y su texto debe leerse. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccentColorsTest {

    private val darkBackground = 0xFF0B0D12.toInt()
    private val lightBackground = 0xFFF4F5F8.toInt()

    @Test
    fun `un acento que ya se distingue no se toca`() {
        val orange = 0xFFFF7A3D.toInt()
        assertThat(AccentColors.visible(orange, darkBackground)).isEqualTo(orange)
    }

    @Test
    fun `un acento casi negro se aclara hasta distinguirse en fondo oscuro`() {
        val almostBlack = 0xFF101820.toInt()
        val shown = AccentColors.visible(almostBlack, darkBackground)
        assertThat(ColorUtils.calculateContrast(shown, darkBackground)).isAtLeast(3.0)
    }

    @Test
    fun `un acento casi blanco se oscurece hasta distinguirse en fondo claro`() {
        val almostWhite = 0xFFF0F0E8.toInt()
        val shown = AccentColors.visible(almostWhite, lightBackground)
        assertThat(ColorUtils.calculateContrast(shown, lightBackground)).isAtLeast(3.0)
    }

    @Test
    fun `el texto sobre el acento tiene contraste legible`() {
        listOf(0xFFFF7A3D, 0xFF3DDC97, 0xFF4FD6F5, 0xFFFFE066, 0xFF7C4DFF, 0xFF1B5E20, 0xFFB71C1C).forEach { argb ->
            val accent = argb.toInt()
            val on = AccentColors.onAccent(accent)
            assertThat(ColorUtils.calculateContrast(on, accent)).isAtLeast(4.5)
        }
    }
}
