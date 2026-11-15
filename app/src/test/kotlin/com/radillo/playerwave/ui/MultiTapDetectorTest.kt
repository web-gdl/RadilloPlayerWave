package com.radillo.playerwave.ui

import com.google.common.truth.Truth.assertThat
import com.radillo.playerwave.ui.common.MultiTapDetector
import org.junit.Test

/** Uno, dos o tres toques sobre la carátula: carátula/visualizador, otro visualizador, pantalla completa. */
class MultiTapDetectorTest {

    private var pending: Runnable? = null
    private val fired = mutableListOf<Int>()
    private val detector = MultiTapDetector(
        windowMs = 280,
        maxTaps = 3,
        schedule = { r, _ -> pending = r },
        cancel = { r -> if (pending === r) pending = null },
        onTaps = { fired += it },
    )

    /** Pasa el margen de espera sin que llegue otro toque. */
    private fun timeout() {
        val r = pending
        pending = null
        r?.run()
    }

    @Test
    fun `un toque se confirma al pasar el margen`() {
        detector.tap()
        assertThat(fired).isEmpty()
        timeout()
        assertThat(fired).containsExactly(1)
    }

    @Test
    fun `dos toques seguidos cuentan como uno doble`() {
        detector.tap()
        detector.tap()
        timeout()
        assertThat(fired).containsExactly(2)
    }

    @Test
    fun `el tercer toque avisa al instante y empieza de cero`() {
        repeat(3) { detector.tap() }
        assertThat(fired).containsExactly(3)
        assertThat(pending).isNull()
        detector.tap()
        timeout()
        assertThat(fired).containsExactly(3, 1).inOrder()
    }

    @Test
    fun `reset olvida los toques pendientes`() {
        detector.tap()
        detector.reset()
        timeout()
        assertThat(fired).isEmpty()
    }
}
