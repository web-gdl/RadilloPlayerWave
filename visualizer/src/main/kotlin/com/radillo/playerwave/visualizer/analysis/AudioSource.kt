package com.radillo.playerwave.visualizer.analysis

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Bloque de PCM crudo entregado por la fuente de audio. */
class PcmBlock(
    /** Muestras intercaladas (L R L R ...) en [-1, 1]. */
    val samples: FloatArray,
    val channelCount: Int,
    val sampleRateHz: Int,
    /** Posición de reproducción (µs) de la primera muestra del bloque. */
    val timestampUs: Long,
)

enum class AudioSourceStatus { IDLE, ACTIVE, UNAVAILABLE }

/**
 * Fuente de PCM para el analizador. La implementación principal vive en `:player`
 * (un AudioProcessor de ExoPlayer); `:visualizer` solo conoce esta interfaz, por lo que
 * una fuente alternativa (por ejemplo `android.media.audiofx.Visualizer`, opcional y con
 * permiso explícito) puede sustituirla sin tocar el motor.
 */
interface AudioSource {
    val status: StateFlow<AudioSourceStatus>
    val blocks: Flow<PcmBlock>
}
