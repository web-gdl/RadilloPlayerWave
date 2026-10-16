package com.radillo.playerwave.visualizer.analysis

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Detector de transitorios por flujo espectral: suma de incrementos positivos del nivel
 * de cada banda entre frames consecutivos, comparada con un umbral adaptativo
 * (media + k·desviación de los últimos ~1,5 s). La salida es una fuerza en [0, 1] con
 * decaimiento rápido, pensada para disparar pulsos y destellos.
 */
class OnsetDetector(
    bandCount: Int,
    private val historySize: Int = 90,
    private val sensitivity: Float = 1.3f,
    private val decayTau: Float = 0.10f,
) {
    private val previous = FloatArray(bandCount)
    private val history = FloatArray(historySize)
    private var historyIndex = 0
    private var filled = 0

    /** Fuerza del último transitorio, con decaimiento. */
    var value = 0f
        private set

    /** Flujo espectral bruto del último frame (útil para pruebas y depuración). */
    var lastFlux = 0f
        private set

    fun update(bands: FloatArray, dtSeconds: Float): Float {
        var flux = 0f
        val n = minOf(bands.size, previous.size)
        for (i in 0 until n) {
            val d = bands[i] - previous[i]
            if (d > 0f) flux += d
            previous[i] = bands[i]
        }
        flux /= n.coerceAtLeast(1)
        lastFlux = flux

        // Estadísticas del historial antes de incorporar el valor actual.
        var mean = 0f
        for (i in 0 until filled) mean += history[i]
        if (filled > 0) mean /= filled
        var variance = 0f
        for (i in 0 until filled) {
            val d = history[i] - mean
            variance += d * d
        }
        if (filled > 1) variance /= (filled - 1)

        history[historyIndex] = flux
        historyIndex = (historyIndex + 1) % historySize
        if (filled < historySize) filled++

        val threshold = mean + sensitivity * sqrt(variance) + MIN_THRESHOLD
        val raw = if (filled >= MIN_HISTORY && flux > threshold) {
            ((flux - threshold) / (threshold + 1e-3f)).coerceIn(0f, 1f)
        } else 0f

        value = max(value * exp(-dtSeconds / decayTau), raw)
        return value
    }

    fun reset() {
        previous.fill(0f)
        history.fill(0f)
        historyIndex = 0
        filled = 0
        value = 0f
        lastFlux = 0f
    }

    private companion object {
        const val MIN_THRESHOLD = 0.004f
        /** No se disparan onsets hasta tener algo de historial, para evitar falsos positivos al arrancar. */
        const val MIN_HISTORY = 8
    }
}
