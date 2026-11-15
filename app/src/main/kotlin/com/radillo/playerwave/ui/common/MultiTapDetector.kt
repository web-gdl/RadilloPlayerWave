package com.radillo.playerwave.ui.common

/**
 * Cuenta toques seguidos (uno, dos o tres) sobre una misma zona. Para distinguirlos hay que esperar
 * [windowMs] tras cada toque por si llega otro; al llegar a [maxTaps] se avisa al instante.
 *
 * [schedule] y [cancel] son los del `Handler` (inyectados para poder probarlo sin Android).
 */
class MultiTapDetector(
    private val windowMs: Long,
    private val maxTaps: Int,
    private val schedule: (Runnable, Long) -> Unit,
    private val cancel: (Runnable) -> Unit,
    private val onTaps: (Int) -> Unit,
) {
    private var count = 0
    private val resolve = Runnable { fire() }

    fun tap() {
        count++
        cancel(resolve)
        if (count >= maxTaps) fire() else schedule(resolve, windowMs)
    }

    /** Olvida los toques pendientes (la vista se destruye o empezó un deslizamiento). */
    fun reset() {
        cancel(resolve)
        count = 0
    }

    private fun fire() {
        val taps = count
        count = 0
        if (taps > 0) onTaps(taps)
    }
}
