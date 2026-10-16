package com.radillo.playerwave.visualizer.analysis

import kotlin.math.abs

/**
 * Historial corto de [AudioFrame]s con su instante de llegada, para alinear la imagen con el
 * sonido: el PCM se analiza antes de sonar (el `AudioTrack` guarda ~0,5 s), así que el render
 * debe mostrar el frame cuya marca temporal coincide con la posición de reproducción actual.
 *
 * Si las marcas temporales no son comparables (cambio de pista sin vaciar la cadena de audio),
 * se recurre al instante de llegada con una latencia estimada.
 */
class FrameHistory(private val capacity: Int = 180) {

    class Entry(val frame: AudioFrame, val arrivalNanos: Long)

    class Selection(val entry: Entry, val matchedByTimestamp: Boolean)

    private val entries = arrayOfNulls<Entry>(capacity)
    private var next = 0
    private var count = 0

    @Synchronized
    fun add(frame: AudioFrame, arrivalNanos: Long) {
        entries[next] = Entry(frame, arrivalNanos)
        next = (next + 1) % capacity
        if (count < capacity) count++
    }

    @Synchronized
    fun latest(): Entry? = if (count == 0) null else entries[(next - 1 + capacity) % capacity]

    @Synchronized
    fun clear() {
        entries.fill(null)
        next = 0
        count = 0
    }

    /**
     * Elige el frame a mostrar.
     * @param positionUs posición de reproducción actual (null si se desconoce).
     * @param nowNanos instante de render.
     * @param latencyNanos latencia estimada entre análisis y sonido.
     */
    @Synchronized
    fun select(positionUs: Long?, nowNanos: Long, latencyNanos: Long): Selection? {
        if (count == 0) return null
        if (positionUs != null) {
            var best: Entry? = null
            var bestDiff = Long.MAX_VALUE
            for (i in 0 until count) {
                val e = entries[i] ?: continue
                val d = abs(e.frame.timestampUs - positionUs)
                if (d < bestDiff) {
                    bestDiff = d
                    best = e
                }
            }
            if (best != null && bestDiff <= MAX_TIMESTAMP_MISMATCH_US) return Selection(best, true)
        }
        val target = nowNanos - latencyNanos
        var best: Entry? = null
        var bestDiff = Long.MAX_VALUE
        for (i in 0 until count) {
            val e = entries[i] ?: continue
            val d = abs(e.arrivalNanos - target)
            if (d < bestDiff) {
                bestDiff = d
                best = e
            }
        }
        return best?.let { Selection(it, false) }
    }

    companion object {
        /** Tolerancia para considerar que una marca temporal corresponde a la posición pedida. */
        const val MAX_TIMESTAMP_MISMATCH_US = 120_000L
    }
}
