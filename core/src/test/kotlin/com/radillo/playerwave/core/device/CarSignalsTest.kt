package com.radillo.playerwave.core.device

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarSignalsTest {

    private var now = 1_000L

    @After
    fun tearDown() = CarSignals.reset()

    @Test
    fun `la reversa se activa y se desactiva`() {
        CarSignals.clock = { now }
        assertTrue(CarSignals.onReverse(true))
        assertTrue(CarSignals.reverseActive)
        assertFalse(CarSignals.onReverse(true))
        assertTrue(CarSignals.onReverse(false))
        assertFalse(CarSignals.reverseActive)
    }

    @Test
    fun `si se pierde el fin de la reversa vuelve sola pasado el tope`() {
        CarSignals.clock = { now }
        CarSignals.onReverse(true)
        now += CarSignals.MAX_REVERSE_MS - 1
        assertTrue(CarSignals.reverseActive)
        now += 1
        assertFalse(CarSignals.reverseActive)
    }

    @Test
    fun `la llave cancela una reversa pendiente`() {
        CarSignals.clock = { now }
        CarSignals.onReverse(true)
        assertTrue(CarSignals.onAcc(false))
        assertFalse(CarSignals.reverseActive)
        assertFalse(CarSignals.onAcc(false))
    }

    @Test
    fun `solo cuentan presionar y pulsacion, y se avisa la primera vez`() {
        assertEquals("siguiente", CarSignals.onKey(32, 1))
        assertNull(CarSignals.onKey(32, 0))
        assertNull(CarSignals.onKey(32, 2))
        assertEquals("play/pausa", CarSignals.onKey(11, 2))
        assertEquals("tecla 99", CarSignals.onKey(99, 1))
        assertEquals(
            "llave sin avisos · reversa inactiva · teclas del volante: siguiente×2, play/pausa×1, tecla 99×1",
            CarSignals.summary(),
        )
    }
}
