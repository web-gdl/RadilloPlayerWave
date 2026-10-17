package com.radillo.playerwave.player.jancar

/** Lo mínimo del reproductor que necesita el manejador de órdenes (en la app, el ExoPlayer). */
interface SourcePlayer {
    val playWhenReady: Boolean
    fun play()
    fun pause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun setVolume(volume: Float)
}

/**
 * Traduce las órdenes del servicio del estéreo a acciones del reproductor, con las mismas reglas
 * que la app de música de fábrica:
 *
 * - **Pausa temporal / detener / salir por el sistema**: si sonaba, se pausa y se recuerda; el
 *   «reanudar» posterior (al colgar, al volver a encender el auto) la vuelve a poner. Si estaba en
 *   pausa, «reanudar» no hace nada: la pausa era del usuario.
 * - **Botones** (volante, panel, lanzador): actúan como si los pulsara el usuario.
 * - Cualquier cambio de reproducir/pausar que no haya provocado el propio manejador es del usuario
 *   y cancela la reanudación pendiente ([onPlayWhenReadyChanged]).
 *
 * Todo ocurre en el hilo principal.
 */
class SourceCommandHandler(private val player: SourcePlayer) {

    /** Qué debe hacer el puente con su registro en el servicio tras una orden. */
    enum class Outcome {
        /** Nada. */
        KEEP,
        /** El servicio pudo cerrar la fuente: volver a registrarse al próximo «reproducir». */
        RELEASED,
        /** El usuario pidió cerrar la app (barra de estado o voz): soltar la fuente. */
        CLOSE,
    }

    /** La música sonaba cuando el sistema la detuvo; se reanudará con su «reanudar». */
    var resumePending: Boolean = false
        private set

    /** Cambio de reproducir/pausar que provocó el propio manejador y aún no llegó. */
    private var expected: Boolean? = null

    fun handle(command: JancarCommand): Outcome = when (command) {
        JancarCommand.Suspend -> {
            pauseForSystem()
            Outcome.KEEP
        }
        JancarCommand.Resume -> {
            if (resumePending) {
                resumePending = false
                if (!player.playWhenReady) {
                    expected = true
                    player.play()
                    // Sin cola no hay nada que poner: no dejar una expectativa colgada.
                    if (!player.playWhenReady) expected = null
                }
            }
            Outcome.KEEP
        }
        JancarCommand.Stop -> {
            pauseForSystem()
            Outcome.RELEASED
        }
        is JancarCommand.Quit -> if (command.source == JancarProtocol.QUIT_SYSTEM) {
            // Al apagar el auto el sistema pide salir a las apps de terceros; la fuente sigue
            // registrada y al encender llegará «reanudar». No se cierra nada.
            pauseForSystem()
            Outcome.KEEP
        } else {
            resumePending = false
            if (player.playWhenReady) player.pause()
            Outcome.CLOSE
        }
        JancarCommand.Pause -> {
            resumePending = false
            player.pause()
            Outcome.KEEP
        }
        JancarCommand.Play -> {
            resumePending = false
            player.play()
            Outcome.KEEP
        }
        JancarCommand.PlayPause -> {
            resumePending = false
            if (player.playWhenReady) player.pause() else player.play()
            Outcome.KEEP
        }
        JancarCommand.Next -> {
            player.next()
            Outcome.KEEP
        }
        JancarCommand.Previous -> {
            player.previous()
            Outcome.KEEP
        }
        is JancarCommand.SeekTo -> {
            player.seekTo(command.positionMs)
            Outcome.KEEP
        }
        is JancarCommand.Volume -> {
            val v = command.volume
            player.setVolume(if (v.isNaN()) 1f else v.coerceIn(0f, 1f))
            Outcome.KEEP
        }
        is JancarCommand.Ignored -> Outcome.KEEP
    }

    /**
     * Llamar en cada cambio de reproducir/pausar. Devuelve `true` si el cambio fue del usuario
     * (de la app, de la notificación o de un botón), `false` si lo provocó el sistema.
     */
    fun onPlayWhenReadyChanged(playWhenReady: Boolean): Boolean {
        val wanted = expected
        expected = null
        if (wanted == playWhenReady) return false
        resumePending = false
        return true
    }

    private fun pauseForSystem() {
        if (!player.playWhenReady) return
        resumePending = true
        expected = false
        player.pause()
        if (player.playWhenReady) expected = null
    }
}
