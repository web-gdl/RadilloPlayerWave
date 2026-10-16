package com.radillo.playerwave.visualizer.analysis

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * FFT compleja radix-2 (Cooley-Tukey iterativa, decimación en tiempo), in-place y sin
 * asignaciones por llamada. Tablas de giro y de inversión de bits precalculadas.
 *
 * Transformada directa: X[k] = Σ x[n]·e^(−2πi·kn/N).
 */
class Fft(val size: Int) {

    init {
        require(size >= 2 && (size and (size - 1)) == 0) { "El tamaño de la FFT debe ser potencia de 2 (recibido $size)" }
    }

    private val levels = Integer.numberOfTrailingZeros(size)
    private val cosTable = FloatArray(size / 2) { cos(2.0 * PI * it / size).toFloat() }
    private val sinTable = FloatArray(size / 2) { sin(2.0 * PI * it / size).toFloat() }
    private val bitReverse = IntArray(size) { Integer.reverse(it) ushr (32 - levels) }

    /** Transforma `re`/`im` in-place. Ambos arrays deben tener longitud [size]. */
    fun transform(re: FloatArray, im: FloatArray) {
        require(re.size == size && im.size == size)
        // Permutación por inversión de bits.
        for (i in 0 until size) {
            val j = bitReverse[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        // Mariposas.
        var halfSize = 1
        while (halfSize < size) {
            val tableStep = size / (halfSize * 2)
            var i = 0
            while (i < size) {
                var k = 0
                var j = i
                val end = i + halfSize
                while (j < end) {
                    val l = j + halfSize
                    val c = cosTable[k]
                    val s = sinTable[k]
                    val tRe = re[l] * c + im[l] * s
                    val tIm = -re[l] * s + im[l] * c
                    re[l] = re[j] - tRe
                    im[l] = im[j] - tIm
                    re[j] += tRe
                    im[j] += tIm
                    k += tableStep
                    j++
                }
                i += halfSize * 2
            }
            halfSize *= 2
        }
    }

    /**
     * Magnitudes de los bins 0 until size/2 (se descarta Nyquist), escaladas con `scale`.
     * `out` debe tener longitud size/2.
     */
    fun magnitudes(re: FloatArray, im: FloatArray, out: FloatArray, scale: Float = 1f) {
        val half = size / 2
        require(out.size >= half)
        for (k in 0 until half) {
            out[k] = sqrt(re[k] * re[k] + im[k] * im[k]) * scale
        }
    }

    companion object {
        /** Ventana de Hann periódica de longitud n. */
        fun hannWindow(n: Int): FloatArray = FloatArray(n) { i -> (0.5 * (1.0 - cos(2.0 * PI * i / n))).toFloat() }
    }
}
