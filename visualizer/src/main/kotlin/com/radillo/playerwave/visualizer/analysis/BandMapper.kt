package com.radillo.playerwave.visualizer.analysis

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Agrupa bins de FFT en bandas espaciadas logarítmicamente entre [minHz] y [maxHz].
 * Cada banda cubre al menos un bin; las bandas graves (pocos bins) y agudas (muchos bins)
 * quedan así comparables.
 */
class BandMapper(
    val sampleRate: Int,
    val fftSize: Int,
    val bandCount: Int,
    minHz: Float,
    maxHz: Float,
) {
    /** Bin inicial (inclusive) de cada banda. */
    val starts = IntArray(bandCount)
    /** Bin final (exclusive) de cada banda. */
    val ends = IntArray(bandCount)
    /** Frecuencia central aproximada de cada banda, en Hz. */
    val centerHz = FloatArray(bandCount)

    init {
        require(sampleRate > 0 && bandCount > 0 && fftSize > 1)
        val spectrumSize = fftSize / 2
        val nyquist = sampleRate / 2f
        val lo = minHz.coerceIn(1f, nyquist - 1f)
        val hi = maxHz.coerceIn(lo + 1f, nyquist)
        val ratio = hi / lo
        var previousEnd = max(1, hzToBin(lo)) // se ignora el bin 0 (continua)
        for (i in 0 until bandCount) {
            val fEnd = lo * ratio.toDouble().pow((i + 1).toDouble() / bandCount).toFloat()
            var end = min(spectrumSize, hzToBin(fEnd))
            if (end <= previousEnd) end = min(spectrumSize, previousEnd + 1)
            starts[i] = min(previousEnd, spectrumSize - 1)
            ends[i] = max(end, starts[i] + 1).coerceAtMost(spectrumSize)
            if (ends[i] <= starts[i]) ends[i] = starts[i] + 1
            centerHz[i] = binToHz((starts[i] + ends[i]) / 2f)
            previousEnd = ends[i]
        }
    }

    private fun hzToBin(hz: Float): Int = (hz * fftSize / sampleRate).roundToInt()
    private fun binToHz(bin: Float): Float = bin * sampleRate / fftSize

    /** Media de magnitudes por banda. `out` debe tener longitud [bandCount]. */
    fun apply(magnitudes: FloatArray, out: FloatArray) {
        for (i in 0 until bandCount) {
            val s = starts[i]
            val e = min(ends[i], magnitudes.size)
            var sum = 0f
            var n = 0
            var k = s
            while (k < e) {
                sum += magnitudes[k]
                n++
                k++
            }
            out[i] = if (n > 0) sum / n else 0f
        }
    }

    /** Índice de la primera banda cuyo centro es ≥ hz (o bandCount si ninguna). */
    fun bandIndexFor(hz: Float): Int {
        for (i in 0 until bandCount) if (centerHz[i] >= hz) return i
        return bandCount
    }
}
