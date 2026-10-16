package com.radillo.playerwave.visualizer.analysis

/**
 * Reloj de reproducción que el visualizador consulta en cada frame de render (hilo principal)
 * para alinear la imagen con el sonido. Lo implementa `:player`; `:visualizer` solo ve la interfaz.
 */
/**
 * Reloj de reproducción que consulta el visualizador. **Se llama desde el hilo de render**, no
 * desde el principal: las implementaciones deben ser seguras entre hilos y no tocar objetos
 * ligados a un hilo (como `MediaController`, que solo admite el hilo principal).
 */
interface PlaybackClock {
    /** Posición de reproducción actual en µs, o null si no hay nada cargado. */
    fun positionUs(): Long?

    /** `true` mientras el reproductor avanza (no en pausa ni en buffering). */
    fun isPlaying(): Boolean
}
