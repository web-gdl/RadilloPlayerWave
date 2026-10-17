package com.radillo.playerwave.player.engine

import com.radillo.playerwave.core.model.Track
import kotlinx.coroutines.flow.StateFlow

enum class RepeatMode { OFF, ALL, ONE }

/**
 * Estado observable de la reproducción, expuesto a la UI sin filtrar tipos de Media3.
 */
data class PlaybackState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = -1,
)

/**
 * Contrato de control de reproducción. La implementación sobre MediaController/ExoPlayer
 * llega en la Fase 1. La UI y el visualizador solo dependen de esta interfaz.
 */
interface PlaybackController {
    val state: StateFlow<PlaybackState>

    fun play()
    fun pause()
    fun togglePlayPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun setShuffle(enabled: Boolean)
    fun setRepeatMode(mode: RepeatMode)

    /** Reemplaza la cola y empieza por `startIndex`. */
    fun playQueue(tracks: List<Track>, startIndex: Int = 0)
    fun addToQueue(tracks: List<Track>)
    fun playNext(tracks: List<Track>)
    fun removeFromQueue(index: Int)
    fun skipToQueueItem(index: Int)

    /**
     * Antes de reescribir el archivo de una canción: si es la actual, el reproductor la suelta
     * (`stop`, conserva cola y posición) para no leer un archivo que cambia bajo sus pies.
     * Devuelve true si había que soltarla.
     */
    fun holdForEdit(trackId: Long): Boolean

    /**
     * Actualiza en la cola los datos de una canción editada (título, portada…) y, si estaba
     * retenida por [holdForEdit], la vuelve a preparar en la misma posición. Con [track] null solo
     * libera la retención (la edición no se guardó).
     */
    fun refreshTrack(track: Track?)
}
