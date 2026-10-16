package com.radillo.playerwave.visualizer.analysis

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SmoothingAndOnsetTest {

    @Test
    fun `el suavizado sube mas rapido de lo que baja`() {
        val s = AttackReleaseSmoother(size = 1, attackTau = 0.02f, releaseTau = 0.2f)
        s.update(floatArrayOf(1f), 0.02f)
        val afterRise = s.values[0]
        assertThat(afterRise).isGreaterThan(0.6f)
        s.update(floatArrayOf(0f), 0.02f)
        val drop = afterRise - s.values[0]
        assertThat(drop).isLessThan(afterRise * 0.15f)
        // Converge al objetivo con el tiempo.
        repeat(200) { s.update(floatArrayOf(0f), 0.02f) }
        assertThat(s.values[0]).isLessThan(0.01f)
    }

    @Test
    fun `el AGC amplifica senales debiles sin pasar del maximo`() {
        val agc = Agc(attackTau = 0.01f, releaseTau = 0.01f, floor = 0.45f, maxGain = 2.2f)
        val loud = agc.update(floatArrayOf(0.95f, 0.5f), 0.1f)
        assertThat(loud).isWithin(0.1f).of(1f)
        var quiet = 1f
        repeat(50) { quiet = agc.update(floatArrayOf(0.2f, 0.1f), 0.1f) }
        assertThat(quiet).isWithin(0.05f).of(2.2f)
    }

    @Test
    fun `un cambio brusco de energia dispara un transitorio y luego decae`() {
        val det = OnsetDetector(bandCount = 8)
        val dt = 1f / 60f
        val quiet = FloatArray(8) { 0.2f }
        // Historial estable: sin transitorios.
        var v = 0f
        repeat(60) { v = det.update(quiet, dt) }
        assertThat(v).isWithin(1e-6f).of(0f)

        val loud = FloatArray(8) { 0.9f }
        val onset = det.update(loud, dt)
        assertThat(onset).isGreaterThan(0.5f)

        // Mantener el nivel alto no es un transitorio nuevo: el valor decae.
        var after = onset
        repeat(30) { after = det.update(loud, dt) }
        assertThat(after).isLessThan(onset * 0.2f)
    }
}
