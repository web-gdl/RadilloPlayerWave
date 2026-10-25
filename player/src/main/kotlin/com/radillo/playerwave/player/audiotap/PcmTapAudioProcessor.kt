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
 *
 * El formato nuevo se aplica al hacer `flush`, no en `configure`: entre ambos todavía puede llegar
 * audio del formato anterior (p. ej. el final de la canción que se va, que el recorte de silencio
 * retenía), y leerlo con el número de canales o la frecuencia nuevos lo desordenaría.
 */
@OptIn(UnstableApi::class)
class PcmTapAudioProcessor(private val source: TapAudioSource) : BaseAudioProcessor() {

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        if (isSupported(inputAudioFormat.encoding)) inputAudioFormat else AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        // inputAudioFormat es el formato ya aplicado (el del audio que llega ahora).
        source.write(inputBuffer, inputAudioFormat.bytesPerFrame)
        replaceOutputBuffer(remaining).put(inputBuffer).flip()
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        val format = inputAudioFormat
        when {
            format == AudioProcessor.AudioFormat.NOT_SET -> Unit // sin configurar todavía
            isSupported(format.encoding) -> source.onConfigured(format.sampleRate, format.channelCount, format.encoding)
            else -> source.onUnsupported()
        }
        source.onFlush(streamMetadata.positionOffsetUs)
    }

    override fun onReset() {
        source.onReset()
    }

    private fun isSupported(encoding: Int): Boolean =
        encoding == C.ENCODING_PCM_16BIT || encoding == C.ENCODING_PCM_FLOAT
}
