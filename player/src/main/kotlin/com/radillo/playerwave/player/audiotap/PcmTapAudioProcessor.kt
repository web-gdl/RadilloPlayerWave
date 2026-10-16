package com.radillo.playerwave.player.audiotap

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * `AudioProcessor` transparente: copia el PCM que atraviesa la cadena de audio de ExoPlayer
 * al [TapAudioSource] y lo deja pasar sin modificarlo. Se ejecuta en el hilo de reproducción,
 * de modo que no bloquea, no asigna memoria por bloque (más allá del buffer de conversión,
 * que se reutiliza) y no toca la UI.
 *
 * Solo acepta PCM de 16 bits y float. Con `enableFloatOutput = false` (valor por defecto del
 * sink) todo el audio llega como 16 bits, incluidas las fuentes de alta resolución.
 */
@OptIn(UnstableApi::class)
class PcmTapAudioProcessor(private val source: TapAudioSource) : BaseAudioProcessor() {

    private var bytesPerFrame = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT -> {
                bytesPerFrame = inputAudioFormat.bytesPerFrame
                source.onConfigured(inputAudioFormat.sampleRate, inputAudioFormat.channelCount, inputAudioFormat.encoding)
                inputAudioFormat
            }
            else -> {
                bytesPerFrame = 0
                source.onUnsupported()
                AudioProcessor.AudioFormat.NOT_SET
            }
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        source.write(inputBuffer, bytesPerFrame)
        replaceOutputBuffer(remaining).put(inputBuffer).flip()
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        source.onFlush(streamMetadata.positionOffsetUs)
    }

    override fun onReset() {
        source.onReset()
    }
}
