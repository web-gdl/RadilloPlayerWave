package com.radillo.playerwave.visualizer.engine

/**
 * Gobernador de calidad: observa la duración de cada frame de render y ajusta un factor
 * [quality] ∈ [minQuality, 1] que las escenas aplican a sus recuentos (partículas, líneas,
 * anillos, barras). No conoce ni al reproductor ni a Compose: solo recibe `dt`.
 *
 * Reglas:
 * - El periodo de refresco se estima como el menor `dt` observado hasta ahora, partiendo de
 *   60 Hz y hasta 144 Hz; nunca se alarga, para que una racha de frames lentos no se confunda
 *   con una pantalla lenta.
 * - Un frame es «tardío» si dura más que `max(1.6 × periodo, 22 ms)`: perder un vsync a 60 Hz,
 *   o bajar de ~45 fps en pantallas de 90/120 Hz.
 * - Al cerrar cada ventana de [windowFrames] frames: si ≥ 20 % fueron tardíos, la calidad baja
 *   (× 0.75); si ninguno lo fue durante [goodWindowsNeeded] ventanas seguidas y la calidad no es
 *   máxima, sube (× 1.2). Si una subida provoca una bajada poco después, la siguiente subida
 *   exige el doble de ventanas buenas (hasta 32), para no oscilar.
 * - Tras cada cambio, cambio de escena o de tamaño hay un periodo de gracia ([warmUpSeconds])
 *   sin decisiones, porque los primeros frames siempre son más lentos.
 * - Un `dt` mayor de 250 ms se interpreta como una pausa (vuelta del segundo plano) y reinicia
 *   la ventana en vez de contarse como tardío.
 */
class QualityGovernor(
    private val minQuality: Float = 0.3f,
    private val windowFrames: Int = 60,
    private val warmUpSeconds: Float = 1.5f,
) {
    var quality: Float = 1f
        private set

    /** Con `false` la calidad vuelve a 1 y no se toma ninguna decisión. */
    var enabled: Boolean = true
        set(value) {
            field = value
            if (!value) quality = 1f
            reset()
        }

    private var frames = 0
    private var late = 0
    private var windowMinDt = Float.MAX_VALUE
    private var period = 1f / 60f
    private var warmUpLeft = warmUpSeconds
    private var goodWindows = 0
    private var goodWindowsNeeded = INITIAL_GOOD_WINDOWS
    private var probing = 0 // ventanas que quedan de «prueba» tras una subida

    /** Marca un cambio de escena o de tamaño: periodo de gracia sin decisiones. */
    fun reset() {
        frames = 0
        late = 0
        windowMinDt = Float.MAX_VALUE
        warmUpLeft = warmUpSeconds
        goodWindows = 0
    }

    /**
     * Procesa un frame de duración [dtSeconds]. Devuelve la nueva calidad si ha cambiado en este
     * frame, o `null` si no.
     */
    fun onFrame(dtSeconds: Float): Float? {
        if (!enabled) return null
        if (dtSeconds > PAUSE_SECONDS) {
            reset()
            return null
        }
        if (warmUpLeft > 0f) {
            warmUpLeft -= dtSeconds
            return null
        }
        if (dtSeconds < windowMinDt) windowMinDt = dtSeconds
        frames++
        if (dtSeconds > maxOf(period * LATE_FACTOR, LATE_FLOOR_SECONDS)) late++
        if (frames < windowFrames) return null

        val ratio = late.toFloat() / frames
        frames = 0
        late = 0
        // El periodo solo puede acortarse: una ventana en la que todos los frames son lentos no
        // debe hacernos creer que la pantalla es lenta (se asume un mínimo de 60 Hz).
        period = minOf(period, windowMinDt.coerceAtLeast(1f / 144f))
        windowMinDt = Float.MAX_VALUE
        if (probing > 0) probing--

        return when {
            ratio >= LATE_RATIO -> {
                goodWindows = 0
                if (probing > 0) {
                    // La última subida fue demasiado optimista: la próxima costará más.
                    goodWindowsNeeded = minOf(goodWindowsNeeded * 2, MAX_GOOD_WINDOWS)
                    probing = 0
                }
                val next = (quality * DOWN_FACTOR).coerceAtLeast(minQuality)
                if (next != quality) {
                    quality = next
                    warmUpLeft = warmUpSeconds
                    next
                } else {
                    null
                }
            }
            ratio == 0f && quality < 1f -> {
                goodWindows++
                if (goodWindows >= goodWindowsNeeded) {
                    goodWindows = 0
                    probing = PROBE_WINDOWS
                    quality = (quality * UP_FACTOR).coerceAtMost(1f)
                    warmUpLeft = warmUpSeconds
                    quality
                } else {
                    null
                }
            }
            else -> {
                goodWindows = 0
                null
            }
        }
    }

    private companion object {
        const val PAUSE_SECONDS = 0.25f
        const val LATE_FACTOR = 1.6f
        const val LATE_FLOOR_SECONDS = 0.022f
        const val LATE_RATIO = 0.2f
        const val DOWN_FACTOR = 0.75f
        const val UP_FACTOR = 1.2f
        const val INITIAL_GOOD_WINDOWS = 4
        const val MAX_GOOD_WINDOWS = 32
        const val PROBE_WINDOWS = 3
    }
}
