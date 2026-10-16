package com.radillo.playerwave.visualizer.analysis

/**
 * Reloj de reproducción que el visualizador consulta en cada frame de render (hilo principal)
 * para alinear la imagen con el sonido. Lo implementa `:player`; `:visualizer` solo ve la interfaz.
 */
interface PlaybackClock {
    /** Posición de reproducción actual en µs, o null si no hay nada cargado. */
    fun positionUs(): Long?

    /** `true` mientras el reproductor avanza (no en pausa ni en buffering). */
    fun isPlaying(): Boolean
}
