package com.radillo.playerwave.data.waveform

import kotlin.math.sqrt

/** Cálculos puros de la onda de la barra de progreso (sin Android, para poder probarlos). */
object WaveformShape {

    /** Columnas de la onda: suficientes para verse como gráfica en 400–500 px y baratas de calcular. */
    const val BUCKETS = 96

    /**
     * Convierte los niveles medidos (RMS por tramo, cualquier escala) en alturas 0–255: se normaliza
     * al tramo más fuerte y se aplica raíz cuadrada, para que los pasajes suaves no desaparezcan.
     * `null` si no hay señal (silencio total o nada medido): entonces se usa la barra normal.
     */
    fun normalize(levels: FloatArray): ByteArray? {
        val max = levels.maxOrNull() ?: return null
        if (!(max > 0f)) return null
        return ByteArray(levels.size) { i ->
            val v = sqrt((levels[i] / max).coerceIn(0f, 1f))
            (v * 255f).toInt().coerceIn(0, 255).toByte()
        }
    }

    /** Altura (0–1) de la columna [index]; los bytes van sin signo. */
    fun level(peaks: ByteArray, index: Int): Float = (peaks[index].toInt() and 0xFF) / 255f
}
