package com.radillo.playerwave.player.jancar

/** Cálculos de tamaño para la carátula que se envía al estéreo (sin depender de Android). */
object ArtSize {

    /**
     * Mayor divisor potencia de 2 que deja la imagen decodificada **al menos** de [max] px en su
     * lado mayor (decodificar barato y escalar después sin perder calidad).
     */
    fun sampleSize(width: Int, height: Int, max: Int): Int {
        if (width <= 0 || height <= 0 || max <= 0) return 1
        var sample = 1
        val longest = maxOf(width, height)
        while (longest / (sample * 2) >= max) sample *= 2
        return sample
    }

    /** Tamaño final que cabe en [max]×[max] conservando la proporción (nunca 0, nunca agranda). */
    fun fitWithin(width: Int, height: Int, max: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 0 to 0
        if (width <= max && height <= max) return width to height
        return if (width >= height) {
            max to (height.toLong() * max / width).toInt().coerceAtLeast(1)
        } else {
            (width.toLong() * max / height).toInt().coerceAtLeast(1) to max
        }
    }
}
