package com.radillo.playerwave.visualizer.analysis

import kotlin.math.min

/**
 * Anillo de PCM estéreo intercalado (L R L R …) para un único productor (hilo de audio) y un
 * único consumidor (hilo de análisis). Sin bloqueos: los contadores absolutos de frames son
 * `@Volatile`; si el consumidor se retrasa más que la capacidad, se descartan los frames más
 * antiguos.
 *
 * También guarda la referencia temporal del flujo (`markBase`) para que el consumidor pueda
 * convertir un índice de frame en posición de reproducción.
 */
class PcmRingBuffer(val capacityFrames: Int) {

    val channels = 2
    private val data = FloatArray(capacityFrames * channels)

    @Volatile private var writeFrames = 0L
    @Volatile private var readFrames = 0L

    @Volatile private var baseFrame = 0L
    @Volatile private var baseUs = 0L
    @Volatile var sampleRate = 48_000
        private set

    /** Frame absoluto donde empezó la última lectura (para calcular su marca temporal). */
    var lastReadStartFrame = 0L
        private set

    /** Productor: escribe `frames` frames estéreo desde `src[offset]`. */
    fun write(src: FloatArray, offset: Int, frames: Int) {
        if (frames <= 0) return
        var remaining = frames
        var srcPos = offset
        var pos = (writeFrames % capacityFrames).toInt()
        while (remaining > 0) {
            val chunk = min(remaining, capacityFrames - pos)
            System.arraycopy(src, srcPos, data, pos * channels, chunk * channels)
            remaining -= chunk
            srcPos += chunk * channels
            pos = (pos + chunk) % capacityFrames
        }
        writeFrames += frames
    }

    /** Frames disponibles para leer (ya descontados los perdidos por desbordamiento). */
    fun available(): Int {
        val w = writeFrames
        val r = readFrames
        val pending = w - r
        return if (pending <= 0) 0 else min(pending, capacityFrames.toLong()).toInt()
    }

    /**
     * Consumidor: lee hasta `maxFrames` frames en `dst` (intercalado). Devuelve los frames leídos.
     * Si hubo desbordamiento, salta a los datos más recientes.
     */
    fun read(dst: FloatArray, maxFrames: Int): Int {
        val w = writeFrames
        var r = readFrames
        if (w - r > capacityFrames) r = w - capacityFrames // desbordamiento: descarta lo antiguo
        val toRead = min(min(maxFrames, (w - r).toInt().coerceAtLeast(0)), dst.size / channels)
        if (toRead <= 0) return 0
        lastReadStartFrame = r
        var remaining = toRead
        var dstPos = 0
        var pos = (r % capacityFrames).toInt()
        while (remaining > 0) {
            val chunk = min(remaining, capacityFrames - pos)
            System.arraycopy(data, pos * channels, dst, dstPos, chunk * channels)
            remaining -= chunk
            dstPos += chunk * channels
            pos = (pos + chunk) % capacityFrames
        }
        readFrames = r + toRead
        return toRead
    }

    /** Productor: fija la referencia temporal (posición de reproducción del siguiente frame). */
    fun markBase(positionUs: Long, sampleRate: Int) {
        baseFrame = writeFrames
        baseUs = positionUs
        if (sampleRate > 0) this.sampleRate = sampleRate
    }

    /** Productor: descarta todo lo pendiente (p. ej. tras un salto de posición). */
    fun discardPending() {
        readFrames = writeFrames
    }

    /** Marca temporal (µs de reproducción) del frame absoluto `frame`. */
    fun timestampUs(frame: Long): Long {
        val rate = sampleRate.coerceAtLeast(1)
        return baseUs + (frame - baseFrame) * 1_000_000L / rate
    }

    fun totalWrittenFrames(): Long = writeFrames
}
