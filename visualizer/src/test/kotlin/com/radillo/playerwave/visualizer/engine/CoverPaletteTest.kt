package com.radillo.playerwave.visualizer.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CoverPaletteTest {

    @Test
    fun `hsv round trip keeps the color`() {
        for (color in intArrayOf(0xFFFF8A3D.toInt(), 0xFF2BD4FF.toInt(), 0xFF39FF88.toInt(), 0xFF8C7BFF.toInt(), 0xFFFFFFFF.toInt())) {
            val hsv = Hsv.of(color)
            assertThat(Hsv.toArgb(hsv[0], hsv[1], hsv[2])).isEqualTo(color)
        }
    }

    @Test
    fun `cover id round trips through byId and keeps the hue of the cover`() {
        val orange = 0xFFFF8A3D.toInt()
        val id = Palettes.coverId(orange)
        assertThat(id).isEqualTo("cover-FF8A3D")
        val palette = Palettes.byId(id)
        assertThat(palette.id).isEqualTo(id)
        assertThat(palette.colors[1]).isEqualTo(orange) // el color de los botones, tal cual
        assertThat(Hsv.of(palette.colors[0])[0]).isWithin(20f).of(Hsv.of(orange)[0])
        // Fondo: casi negro teñido del mismo tono.
        assertThat(Hsv.of(palette.background)[2]).isLessThan(0.1f)
        // La misma consulta devuelve la misma paleta (sin crear otra cada vez).
        assertThat(Palettes.byId(id)).isSameInstanceAs(palette)
    }

    @Test
    fun `dull or dark covers are brightened so the visualizer stays visible`() {
        val darkGrayBlue = 0xFF1C2430.toInt()
        val palette = Palettes.fromAccent(darkGrayBlue)
        val hsv = Hsv.of(palette.colors[1])
        assertThat(hsv[2]).isAtLeast(0.84f)
        assertThat(hsv[1]).isAtLeast(0.44f)
    }

    @Test
    fun `plain cover id or garbage falls back to aurora`() {
        assertThat(Palettes.byId(Palettes.COVER_ID)).isSameInstanceAs(Palettes.AURORA)
        assertThat(Palettes.byId("cover-zzz")).isSameInstanceAs(Palettes.AURORA)
        assertThat(Palettes.byId("ember")).isSameInstanceAs(Palettes.EMBER)
    }
}
