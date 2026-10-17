package com.radillo.playerwave.player.jancar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Decodificación de órdenes, estado publicado, tamaño de carátula y resumen de diagnóstico. */
class JancarProtocolTest {

    /** Argumentos en el orden en que el servicio los escribe. */
    private class Args(vararg values: Any?) : JancarCommand.Args {
        private val queue = ArrayDeque(values.toList())
        val left: Int get() = queue.size
        override fun readInt() = queue.removeFirst() as Int
        override fun readFloat() = queue.removeFirst() as Float
        override fun readString() = queue.removeFirst() as String?
    }

    @Test
    fun `los codigos de la interfaz se traducen a ordenes`() {
        assertEquals(JancarCommand.Suspend, JancarCommand.decode(1, Args()))
        assertEquals(JancarCommand.Resume, JancarCommand.decode(2, Args()))
        assertEquals(JancarCommand.Pause, JancarCommand.decode(3, Args()))
        assertEquals(JancarCommand.Play, JancarCommand.decode(4, Args()))
        assertEquals(JancarCommand.PlayPause, JancarCommand.decode(5, Args()))
        assertEquals(JancarCommand.Quit(0), JancarCommand.decode(6, Args(0)))
        assertEquals(JancarCommand.Stop, JancarCommand.decode(7, Args()))
        assertEquals(JancarCommand.Volume(0.4f), JancarCommand.decode(8, Args(0.4f)))
        assertEquals(JancarCommand.Next, JancarCommand.decode(9, Args()))
        assertEquals(JancarCommand.Previous, JancarCommand.decode(10, Args()))
        assertEquals(JancarCommand.SeekTo(90_000), JancarCommand.decode(14, Args(90_000)))
        assertNull(JancarCommand.decode(0, Args()))
        assertNull(JancarCommand.decode(19, Args()))
    }

    @Test
    fun `las ordenes ignoradas consumen todos sus argumentos`() {
        val cases = mapOf(
            11 to Args(3),
            12 to Args(1),
            13 to Args(0),
            15 to Args(2),
            16 to Args(),
            17 to Args("título", null),
            18 to Args(1, 2),
        )
        for ((code, args) in cases) {
            val command = JancarCommand.decode(code, args)
            assertTrue("código $code", command is JancarCommand.Ignored)
            assertEquals("código $code", 0, args.left)
        }
    }

    @Test
    fun `una busqueda negativa se lleva a cero`() {
        assertEquals(JancarCommand.SeekTo(0), JancarCommand.decode(14, Args(-5)))
    }

    @Test
    fun `estado publicado segun el reproductor`() {
        assertEquals(JancarProtocol.STATE_PLAYING, JancarProtocol.stateFor(playWhenReady = true, ended = false, hasItems = true))
        assertEquals(JancarProtocol.STATE_PAUSED, JancarProtocol.stateFor(playWhenReady = false, ended = false, hasItems = true))
        assertEquals(JancarProtocol.STATE_STOPPED, JancarProtocol.stateFor(playWhenReady = true, ended = true, hasItems = true))
        assertEquals(JancarProtocol.STATE_STOPPED, JancarProtocol.stateFor(playWhenReady = false, ended = false, hasItems = false))
        assertEquals(JancarProtocol.STATE_STOPPED, JancarProtocol.stateFor(playWhenReady = true, ended = false, hasItems = true, failed = true))
    }

    @Test
    fun `la caratula cabe en 240 px sin deformarse`() {
        assertEquals(240 to 240, ArtSize.fitWithin(1000, 1000, 240))
        assertEquals(240 to 135, ArtSize.fitWithin(1920, 1080, 240))
        assertEquals(135 to 240, ArtSize.fitWithin(1080, 1920, 240))
        assertEquals(200 to 100, ArtSize.fitWithin(200, 100, 240))
        assertEquals(240 to 1, ArtSize.fitWithin(5000, 2, 240))
        assertEquals(0 to 0, ArtSize.fitWithin(0, 10, 240))
    }

    @Test
    fun `el muestreo decodifica barato sin quedar por debajo del tamano final`() {
        assertEquals(4, ArtSize.sampleSize(1000, 1000, 240))
        assertEquals(1, ArtSize.sampleSize(300, 300, 240))
        assertEquals(1, ArtSize.sampleSize(200, 100, 240))
        assertEquals(8, ArtSize.sampleSize(3000, 2000, 240))
        assertEquals(1, ArtSize.sampleSize(0, 0, 240))
        for (side in listOf(240, 479, 480, 960, 1500, 4000)) {
            val s = ArtSize.sampleSize(side, side, 240)
            assertTrue("lado $side muestreo $s", side / s >= 240)
        }
    }

    @Test
    fun `el resumen cuenta las ordenes`() {
        JancarStatus.reset()
        assertEquals("sin comprobar", JancarStatus.summary())
        JancarStatus.link = JancarStatus.Link.CONNECTED
        JancarStatus.registered = true
        JancarStatus.count("siguiente")
        JancarStatus.count("siguiente")
        JancarStatus.count("pausa")
        assertEquals("conectado · registrada como fuente · órdenes: siguiente×2, pausa×1", JancarStatus.summary())
        JancarStatus.reset()
    }
}
