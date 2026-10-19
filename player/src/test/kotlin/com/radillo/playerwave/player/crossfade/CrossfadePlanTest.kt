package com.radillo.playerwave.player.crossfade

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CrossfadePlanTest {

    @Test
    fun `solo se mezcla con siguiente cancion, sin repetir una y si la cancion da para ello`() {
        assertTrue(CrossfadePlan.eligible(fadeMs = 6_000, durationMs = 200_000, hasNext = true, repeatOne = false))
        assertFalse(CrossfadePlan.eligible(fadeMs = 0, durationMs = 200_000, hasNext = true, repeatOne = false))
        assertFalse(CrossfadePlan.eligible(fadeMs = 6_000, durationMs = 200_000, hasNext = false, repeatOne = false))
        assertFalse(CrossfadePlan.eligible(fadeMs = 6_000, durationMs = 200_000, hasNext = true, repeatOne = true))
        // 2×6 s + 5 s = 17 s mínimo.
        assertFalse(CrossfadePlan.eligible(fadeMs = 6_000, durationMs = 16_999, hasNext = true, repeatOne = false))
        assertTrue(CrossfadePlan.eligible(fadeMs = 6_000, durationMs = 17_000, hasNext = true, repeatOne = false))
        assertFalse(CrossfadePlan.eligible(fadeMs = 6_000, durationMs = -1, hasNext = true, repeatOne = false))
    }

    @Test
    fun `se prepara poco antes de la mezcla y no despues del margen`() {
        val duration = 200_000L
        val fade = 6_000L
        assertEquals(194_000L, CrossfadePlan.startAt(duration, fade))
        assertFalse(CrossfadePlan.shouldPrepare(192_499, duration, fade))
        assertTrue(CrossfadePlan.shouldPrepare(192_500, duration, fade))
        assertTrue(CrossfadePlan.shouldPrepare(194_000, duration, fade))
        assertFalse(CrossfadePlan.shouldPrepare(194_600, duration, fade))
    }

    @Test
    fun `curvas de igual potencia`() {
        assertEquals(0f to 1f, CrossfadePlan.gains(0f).let { r(it.first) to r(it.second) })
        assertEquals(1f to 0f, CrossfadePlan.gains(1f).let { r(it.first) to r(it.second) })
        assertEquals(1f to 0f, CrossfadePlan.gains(5f).let { r(it.first) to r(it.second) })
        for (i in 0..20) {
            val (a, b) = CrossfadePlan.gains(i / 20f)
            assertTrue(abs(a * a + b * b - 1f) < 1e-4f)
        }
        val (mid, _) = CrossfadePlan.gains(0.5f)
        assertEquals(0.7071f, mid, 1e-3f)
    }

    @Test
    fun `al saltar a mano la mezcla es corta`() {
        assertEquals(3_000L, CrossfadePlan.manualFadeMs(12_000))
        assertEquals(2_000L, CrossfadePlan.manualFadeMs(2_000))
        assertEquals(0L, CrossfadePlan.manualFadeMs(0))
    }

    @Test
    fun `no se mezcla un salto si a la cancion casi no le queda`() {
        assertTrue(CrossfadePlan.skipEligible(positionMs = 60_000, durationMs = 200_000))
        assertTrue(CrossfadePlan.skipEligible(positionMs = 60_000, durationMs = 0)) // duración desconocida
        assertFalse(CrossfadePlan.skipEligible(positionMs = 198_600, durationMs = 200_000))
    }

    @Test
    fun `el salto espera a que el auxiliar este listo y alineado, sin dejar silencio`() {
        val start = 60_500L
        // Mientras el auxiliar carga, la canción actual sigue sonando en el principal.
        assertEquals(CrossfadePlan.Skip.WAIT, CrossfadePlan.skipDecision(false, 60_100, start, 100))
        // Listo antes de tiempo: espera a que el principal llegue al punto.
        assertEquals(CrossfadePlan.Skip.WAIT, CrossfadePlan.skipDecision(true, 60_300, start, 300))
        assertEquals(CrossfadePlan.Skip.START, CrossfadePlan.skipDecision(true, 60_490, start, 490))
        // Listo tarde: arranca ya (repite unas décimas que se están apagando).
        assertEquals(CrossfadePlan.Skip.START, CrossfadePlan.skipDecision(true, 61_000, start, 1_000))
        // Nunca listo: salto normal.
        assertEquals(CrossfadePlan.Skip.WAIT, CrossfadePlan.skipDecision(false, 61_400, start, 1_499))
        assertEquals(CrossfadePlan.Skip.GIVE_UP, CrossfadePlan.skipDecision(false, 61_500, start, 1_500))
    }

    private fun r(v: Float) = Math.round(v * 1000) / 1000f
}
