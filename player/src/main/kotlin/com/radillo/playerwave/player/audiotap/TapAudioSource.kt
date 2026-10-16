package com.radillo.playerwave.player.audiotap

import androidx.media3.common.C
import com.radillo.playerwave.visualizer.analysis.AudioSource
import com.radillo.playerwave.visualizer.analysis.AudioSourceStatus
import com.radillo.playerwave.visualizer.analysis.PcmBlock
import com.radillo.playerwave.visualizer.analysis.PcmRingBuffer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import java.nio.ByteBuffer

/**
 * Lado consumidor del tap de audio. El [PcmTapAudioProcessor] escribe en el anillo desde el
 * hilo de reproducción; `blocks` entrega bloques estéreo en float al analizador.
 *
 * Siempre entrega 2 canales: mono se duplica y, con más de dos canales, se toman los dos
 * primeros (frontal izquierdo y derecho).
 */
class TapAudioSource : AudioSource {

    private val ring = PcmRingBuffer(capacityFrames = RING_FRAMES)
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private val _status = MutableStateFlow(AudioSourceStatus.IDLE)
    override val status: StateFlow<AudioSourceStatus> = _status

    @Volatile private var sampleRate = 48_000
    @Volatile private var channels = 2
    @Volatile private var encoding = C.ENCODING_PCM_16BIT
    private var conversion = FloatArray(0)

    /** Frames escritos desde la creación (diagnóstico y pruebas). */
    val writtenFrames: Long get() = ring.totalWrittenFrames()

    // ---- Productor (hilo de audio) ----

    fun onConfigured(sampleRate: Int, channelCount: Int, encoding: Int) {
        this.sampleRate = sampleRate
        this.channels = channelCount
        this.encoding = encoding
        ring.markBase(ring.timestampUs(ring.totalWrittenFrames()), sampleRate)
        _status.value = AudioSourceStatus.ACTIVE
    }

    fun onUnsupported() {
        _status.value = AudioSourceStatus.UNAVAILABLE
    }

    fun onFlush(positionOffsetUs: Long) {
        ring.discardPending()
        ring.markBase(positionOffsetUs, sampleRate)
    }

    fun onReset() {
        ring.discardPending()
        _status.value = AudioSourceStatus.IDLE
    }

    /** Copia las muestras del buffer (sin mover su posición) al anillo. */
    fun write(input: ByteBuffer, bytesPerFrame: Int) {
        if (bytesPerFrame <= 0) return
        val frames = input.remaining() / bytesPerFrame
        if (frames <= 0) return
        if (conversion.size < frames * 2) conversion = FloatArray(frames * 2)
        val ch = channels
        val start = input.position()
        when (encoding) {
            C.ENCODING_PCM_16BIT -> {
                // Lectura explícita little-endian (C.ENCODING_PCM_16BIT), independiente del orden del buffer.
                for (f in 0 until frames) {
                    val idx = start + f * bytesPerFrame
                    val l = readShortLe(input, idx) * SHORT_SCALE
                    val r = if (ch > 1) readShortLe(input, idx + 2) * SHORT_SCALE else l
                    conversion[f * 2] = l
                    conversion[f * 2 + 1] = r
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                val floats = input.asFloatBuffer()
                for (f in 0 until frames) {
                    val base = f * ch
                    val l = floats.get(base)
                    val r = if (ch > 1) floats.get(base + 1) else l
                    conversion[f * 2] = l
                    conversion[f * 2 + 1] = r
                }
            }
            else -> return
        }
        ring.write(conversion, 0, frames)
        signal.trySend(Unit)
    }

    private fun readShortLe(buffer: ByteBuffer, index: Int): Int {
        val lo = buffer.get(index).toInt() and 0xFF
        val hi = buffer.get(index + 1).toInt()
        return (hi shl 8) or lo
    }

    // ---- Consumidor (hilo de análisis) ----

    override val blocks: Flow<PcmBlock> = flow {
        val buffer = FloatArray(MAX_BLOCK_FRAMES * 2)
        while (true) {
            signal.receive()
            while (true) {
                val n = ring.read(buffer, MAX_BLOCK_FRAMES)
                if (n == 0) break
                val timestampUs = ring.timestampUs(ring.lastReadStartFrame)
                emit(PcmBlock(buffer.copyOf(n * 2), 2, ring.sampleRate, timestampUs))
            }
        }
    }

    /** Frames disponibles sin leer (pruebas). */
    fun availableFrames(): Int = ring.available()

    companion object {
        /** ~2 s a 48 kHz: holgura suficiente para que el análisis se retrase sin perder datos. */
        const val RING_FRAMES = 96_000
        const val MAX_BLOCK_FRAMES = 2048
        private const val SHORT_SCALE = 1f / 32_768f
    }
}
