package com.radillo.playerwave.data.waveform

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import java.nio.ByteOrder

/**
 * Mide el volumen de una canción en [WaveformShape.BUCKETS] puntos repartidos a lo largo de ella.
 *
 * No decodifica la canción entera (en el estéreo, un Cortex-A7, serían muchos segundos de CPU
 * compitiendo con la reproducción): salta a cada punto, decodifica unas pocas tramas y mide su RMS.
 * Es una gráfica «simple» del sonido, suficiente para orientarse en la barra de progreso.
 */
internal object WaveformExtractor {

    private const val FRAMES_PER_BUCKET = 4
    private const val OUTPUTS_PER_BUCKET = 2
    private const val STEP_TIMEOUT_US = 8_000L
    private const val MAX_STEPS_PER_BUCKET = 30
    /** Tope total: si el decodificador del equipo va muy lento se abandona y queda la barra normal. */
    private const val DEADLINE_MS = 25_000L

    fun extract(context: Context, uri: Uri, isCancelled: () -> Boolean): ByteArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val trackIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            if (durationUs <= 0L) return null
            extractor.selectTrack(trackIndex)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val levels = FloatArray(WaveformShape.BUCKETS)
            val info = MediaCodec.BufferInfo()
            val deadline = SystemClock.elapsedRealtime() + DEADLINE_MS
            var floatPcm = false
            for (bucket in 0 until WaveformShape.BUCKETS) {
                if (isCancelled() || SystemClock.elapsedRealtime() > deadline) return null
                val timeUs = (durationUs * (bucket + 0.5) / WaveformShape.BUCKETS).toLong()
                extractor.seekTo(timeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                codec.flush()

                var fed = 0
                var outputs = 0
                var sumSquares = 0.0
                var samples = 0L
                var steps = 0
                var endOfInput = false
                while (outputs < OUTPUTS_PER_BUCKET && steps++ < MAX_STEPS_PER_BUCKET) {
                    if (fed < FRAMES_PER_BUCKET && !endOfInput) {
                        val inIndex = codec.dequeueInputBuffer(STEP_TIMEOUT_US)
                        if (inIndex >= 0) {
                            val buffer = codec.getInputBuffer(inIndex)
                            val size = if (buffer != null) extractor.readSampleData(buffer, 0) else -1
                            if (size > 0) {
                                codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                                fed++
                            } else {
                                // Final del archivo: se devuelve el búfer vacío y no se alimenta más en este tramo.
                                codec.queueInputBuffer(inIndex, 0, 0, 0L, 0)
                                endOfInput = true
                            }
                        }
                    }
                    val outIndex = codec.dequeueOutputBuffer(info, STEP_TIMEOUT_US)
                    when {
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val out = codec.outputFormat
                            floatPcm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                                out.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                                out.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        }
                        outIndex >= 0 -> {
                            val buffer = codec.getOutputBuffer(outIndex)
                            if (buffer != null && info.size > 0) {
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                buffer.order(ByteOrder.LITTLE_ENDIAN)
                                if (floatPcm) {
                                    val floats = buffer.asFloatBuffer()
                                    var i = 0
                                    val n = floats.remaining()
                                    while (i < n) {
                                        val s = floats.get(i)
                                        sumSquares += (s * s).toDouble()
                                        samples++
                                        i += 4 // una de cada cuatro muestras basta para el nivel
                                    }
                                } else {
                                    val shorts = buffer.asShortBuffer()
                                    var i = 0
                                    val n = shorts.remaining()
                                    while (i < n) {
                                        val s = shorts.get(i) / 32768f
                                        sumSquares += (s * s).toDouble()
                                        samples++
                                        i += 4
                                    }
                                }
                                outputs++
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                        }
                        endOfInput && fed == 0 -> break // no hay nada que decodificar en este tramo
                    }
                }
                levels[bucket] = if (samples > 0) Math.sqrt(sumSquares / samples).toFloat() else 0f
            }
            return WaveformShape.normalize(levels)
        } catch (e: Exception) {
            // Formato sin decodificador, archivo dañado o decodificador caprichoso: barra normal.
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }
}
