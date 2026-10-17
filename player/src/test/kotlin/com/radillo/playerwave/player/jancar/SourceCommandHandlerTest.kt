package com.radillo.playerwave.player.jancar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Reglas de las órdenes del estéreo: qué se pausa, qué se reanuda y qué cuenta como acción del
 * usuario. El reproductor falso avisa de cada cambio como lo hace el servicio real.
 */
class SourceCommandHandlerTest {

    private class FakePlayer : SourcePlayer {
        override var playWhenReady = false
        var currentVolume = 1f
        val calls = mutableListOf<String>()
        /** Cambios de reproducir/pausar y si el manejador los atribuyó al usuario. */
        val changes = mutableListOf<Pair<Boolean, Boolean>>()
        lateinit var handler: SourceCommandHandler

        private fun set(value: Boolean) {
            if (playWhenReady == value) return
            playWhenReady = value
            changes += value to handler.onPlayWhenReadyChanged(value)
        }

        override fun play() { calls += "play"; set(true) }
        override fun pause() { calls += "pause"; set(false) }
        override fun next() { calls += "next" }
        override fun previous() { calls += "previous" }
        override fun seekTo(positionMs: Long) { calls += "seek $positionMs" }
        override fun setVolume(volume: Float) { currentVolume = volume }

        /** El usuario pulsa en la app. */
        fun userPlay() = set(true)
        fun userPause() = set(false)
    }

    private lateinit var player: FakePlayer
    private lateinit var handler: SourceCommandHandler

    @Before
    fun setUp() {
        player = FakePlayer()
        handler = SourceCommandHandler(player)
        player.handler = handler
    }

    @Test
    fun `una pausa temporal con musica sonando se reanuda despues`() {
        player.userPlay()
        handler.handle(JancarCommand.Suspend)
        assertFalse(player.playWhenReady)
        assertTrue(handler.resumePending)

        handler.handle(JancarCommand.Resume)
        assertTrue(player.playWhenReady)
        assertFalse(handler.resumePending)
        // Los dos cambios los provocó el sistema, no el usuario.
        assertEquals(listOf(true to true, false to false, true to false), player.changes)
    }

    @Test
    fun `reanudar no pone musica si el usuario la tenia en pausa`() {
        handler.handle(JancarCommand.Suspend)
        handler.handle(JancarCommand.Resume)
        assertFalse(player.playWhenReady)
        assertTrue(player.calls.isEmpty())
    }

    @Test
    fun `apagar el auto detiene y al encender vuelve a sonar`() {
        player.userPlay()
        // Orden real del sistema: detener y luego pedir salir a las apps de terceros.
        assertEquals(SourceCommandHandler.Outcome.RELEASED, handler.handle(JancarCommand.Stop))
        assertEquals(SourceCommandHandler.Outcome.KEEP, handler.handle(JancarCommand.Quit(JancarProtocol.QUIT_SYSTEM)))
        assertFalse(player.playWhenReady)
        assertTrue(handler.resumePending)

        handler.handle(JancarCommand.Resume)
        assertTrue(player.playWhenReady)
    }

    @Test
    fun `si el usuario pausa durante la pausa temporal ya no se reanuda`() {
        player.userPlay()
        handler.handle(JancarCommand.Suspend)
        player.userPlay()
        player.userPause()
        assertFalse(handler.resumePending)
        handler.handle(JancarCommand.Resume)
        assertFalse(player.playWhenReady)
    }

    @Test
    fun `los botones del volante cuentan como acciones del usuario`() {
        handler.handle(JancarCommand.PlayPause)
        assertTrue(player.playWhenReady)
        handler.handle(JancarCommand.Next)
        handler.handle(JancarCommand.Previous)
        handler.handle(JancarCommand.SeekTo(30_000))
        handler.handle(JancarCommand.PlayPause)
        assertFalse(player.playWhenReady)
        assertEquals(listOf("play", "next", "previous", "seek 30000", "pause"), player.calls)
        assertEquals(listOf(true to true, false to true), player.changes)
    }

    @Test
    fun `una pausa del volante cancela la reanudacion pendiente`() {
        player.userPlay()
        handler.handle(JancarCommand.Suspend)
        handler.handle(JancarCommand.Pause)
        assertFalse(handler.resumePending)
        handler.handle(JancarCommand.Resume)
        assertFalse(player.playWhenReady)
    }

    @Test
    fun `salir desde la barra de estado pausa y suelta la fuente`() {
        player.userPlay()
        assertEquals(SourceCommandHandler.Outcome.CLOSE, handler.handle(JancarCommand.Quit(1)))
        assertFalse(player.playWhenReady)
        assertFalse(handler.resumePending)
        // Fue decisión del usuario.
        assertEquals(false to true, player.changes.last())
    }

    @Test
    fun `el volumen de atenuacion se limita a 0-1`() {
        handler.handle(JancarCommand.Volume(0.4f))
        assertEquals(0.4f, player.currentVolume, 0.0001f)
        handler.handle(JancarCommand.Volume(3f))
        assertEquals(1f, player.currentVolume, 0.0001f)
        handler.handle(JancarCommand.Volume(Float.NaN))
        assertEquals(1f, player.currentVolume, 0.0001f)
        handler.handle(JancarCommand.Volume(-1f))
        assertEquals(0f, player.currentVolume, 0.0001f)
    }

    @Test
    fun `detener dos veces no pierde la reanudacion`() {
        player.userPlay()
        handler.handle(JancarCommand.Stop)
        handler.handle(JancarCommand.Suspend)
        handler.handle(JancarCommand.Stop)
        assertTrue(handler.resumePending)
        handler.handle(JancarCommand.Resume)
        assertTrue(player.playWhenReady)
    }

    @Test
    fun `las ordenes ignoradas no tocan el reproductor`() {
        handler.handle(JancarCommand.Ignored("favorito"))
        assertTrue(player.calls.isEmpty())
    }
}
