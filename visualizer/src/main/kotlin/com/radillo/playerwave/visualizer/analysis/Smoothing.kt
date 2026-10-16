package com.radillo.playerwave.visualizer.analysis

import kotlin.math.exp
import kotlin.math.max

/**
 * Suavizado exponencial asimétrico: sube rápido (ataque) y baja despacio (liberación).
 * Las constantes de tiempo están en segundos, de modo que el resultado no depende de la
 * tasa de frames.
 */
class AttackReleaseSmoother(
    size: Int,
    private val attackTau: Float,
    private val releaseTau: Float,
) {
    val values = FloatArray(size)

    fun update(target: FloatArray, dtSeconds: Float) {
        val attack = coefficient(dtSeconds, attackTau)
        val release = coefficient(dtSeconds, releaseTau)
        val n = minOf(values.size, target.size)
        for (i in 0 until n) {
            val v = values[i]
            val t = target[i]
            values[i] = v + (t - v) * (if (t > v) attack else release)
        }
    }

    fun reset() = values.fill(0f)

    companion object {
        /** Fracción del camino recorrida en `dt` con una constante de tiempo `tau`. */
        fun coefficient(dtSeconds: Float, tau: Float): Float =
            if (tau <= 0f) 1f else (1f - exp(-dtSeconds / tau)).coerceIn(0f, 1f)
    }
}

/** Versión escalar del suavizado asimétrico. */
class ScalarSmoother(private val attackTau: Float, private val releaseTau: Float) {
    var value = 0f
        private set

    fun update(target: Float, dtSeconds: Float): Float {
        val k = AttackReleaseSmoother.coefficient(dtSeconds, if (target > value) attackTau else releaseTau)
        value += (target - value) * k
        return value
    }

    fun reset() { value = 0f }
}

/**
 * Control automático de ganancia lento: estima la envolvente del nivel máximo de las bandas
 * y devuelve una ganancia en [1, maxGain] para que las grabaciones silenciosas sigan
 * llenando la escala sin amplificar el ruido de las muy fuertes.
 */
class Agc(
    private val attackTau: Float = 0.4f,
    private val releaseTau: Float = 3.5f,
    private val floor: Float = 0.45f,
    private val maxGain: Float = 2.2f,
) {
    private var envelope = 1f

    /** Actualiza la envolvente con `levels` (0..1) y devuelve la ganancia a aplicar. */
    fun update(levels: FloatArray, dtSeconds: Float): Float {
        var peak = 0f
        for (v in levels) peak = max(peak, v)
        val k = AttackReleaseSmoother.coefficient(dtSeconds, if (peak > envelope) attackTau else releaseTau)
        envelope += (peak - envelope) * k
        return (1f / max(envelope, floor)).coerceIn(1f, maxGain)
    }

    fun reset() { envelope = 1f }
}
