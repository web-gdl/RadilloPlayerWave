package com.radillo.playerwave.visualizer.render

import kotlin.math.sqrt

/**
 * Tamaño del búfer en el que se dibuja el visualizador.
 *
 * Dibujando con la CPU, el coste de un frame crece con los píxeles: en el autoestéreo, el recuadro
 * de la carátula (360×360 ≈ 130 000 px) va fluido, pero la pantalla completa (1024×600 ≈ 614 000 px)
 * son casi cinco veces más píxeles por frame y el equipo no da abasto. Con un presupuesto de
 * píxeles, la superficie se dibuja más pequeña y el sistema la amplía al tamaño de la vista (lo
 * hace el compositor, sin coste para la app): mismas formas, algo más suaves, y fluidas.
 */
object RenderSize {

    /**
     * Tamaño de búfer para una vista de [width]×[height] sin pasar de [maxPixels] (0 = sin tope).
     * Mantiene la proporción; devuelve el tamaño original si ya cabe. Empaquetado en un `Long`
     * (ancho en los 32 bits altos) para no asignar memoria; ver [widthOf] y [heightOf].
     */
    fun fit(width: Int, height: Int, maxPixels: Int): Long {
        if (width <= 0 || height <= 0) return pack(0, 0)
        val pixels = width.toLong() * height
        if (maxPixels <= 0 || pixels <= maxPixels) return pack(width, height)
        val scale = sqrt(maxPixels.toDouble() / pixels)
        // Pares: algunos compositores escalan peor los tamaños impares.
        val w = ((width * scale).toInt() and 1.inv()).coerceAtLeast(2)
        val h = ((height * scale).toInt() and 1.inv()).coerceAtLeast(2)
        return pack(w, h)
    }

    fun widthOf(packed: Long): Int = (packed ushr 32).toInt()
    fun heightOf(packed: Long): Int = (packed and 0xFFFFFFFFL).toInt()

    private fun pack(width: Int, height: Int): Long = (width.toLong() shl 32) or (height.toLong() and 0xFFFFFFFFL)

    /**
     * Pausa entre un frame y el siguiente, en nanosegundos. El hilo de render mantiene tomado el
     * cerrojo de la superficie durante todo el frame y lo vuelve a tomar enseguida; sin una pausa,
     * el hilo principal (que necesita ese mismo cerrojo para cambiar el tamaño o la visibilidad de
     * la superficie, p. ej. al pasar a pantalla completa) puede quedarse esperando varios frames
     * seguidos: la app «se congela» aunque la música siga. Cuanto más lento el frame, más pausa.
     */
    fun yieldNanos(frameNanos: Long): Long = (frameNanos / 8).coerceIn(MIN_YIELD_NANOS, MAX_YIELD_NANOS)

    const val MIN_YIELD_NANOS = 3_000_000L
    const val MAX_YIELD_NANOS = 20_000_000L
}
