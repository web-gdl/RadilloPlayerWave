package com.radillo.playerwave.player.crossfade

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cuándo y cómo se mezclan dos canciones (sin Android, para poder probarlo).
 *
 * La mezcla empieza [fadeMs] antes del final de la canción. Un poco antes ([PREPARE_LEAD_MS]) se
 * prepara el reproductor auxiliar con el final de la canción actual, para que cuando el principal
 * salte a la siguiente el auxiliar ya esté listo y no haya hueco.
 */
object CrossfadePlan {

    /** Margen para preparar el reproductor auxiliar antes de que empiece la mezcla. */
    const val PREPARE_LEAD_MS = 1_500L

    /** Canciones más cortas que esto (además de la mezcla) no se mezclan: se comerían enteras. */
    const val MIN_EXTRA_MS = 5_000L

    /** Si el auxiliar no está listo pasado este margen, se abandona la mezcla (transición normal). */
    const val START_GRACE_MS = 600L

    /** Mezcla al saltar a mano: corta, para que la canción elegida se imponga enseguida. */
    const val MANUAL_MAX_MS = 3_000L

    /** Si la última posición anotada es más vieja que esto, no se retoma la canción anterior. */
    const val MAX_STALE_MS = 1_500L

    fun manualFadeMs(fadeMs: Long): Long = fadeMs.coerceAtMost(MANUAL_MAX_MS).coerceAtLeast(0L)

    /** Dónde iba la canción anterior: lo anotado más el tiempo transcurrido desde entonces. */
    fun estimatePosition(lastPositionMs: Long, elapsedMs: Long): Long =
        (lastPositionMs.coerceAtLeast(0L) + elapsedMs.coerceIn(0L, MAX_STALE_MS))

    /** ¿Se puede mezclar esta canción? */
    fun eligible(fadeMs: Long, durationMs: Long, hasNext: Boolean, repeatOne: Boolean): Boolean =
        fadeMs > 0 && hasNext && !repeatOne && durationMs > 0 && durationMs >= 2 * fadeMs + MIN_EXTRA_MS

    /** Posición de la canción actual en la que empieza la mezcla. */
    fun startAt(durationMs: Long, fadeMs: Long): Long = (durationMs - fadeMs).coerceAtLeast(0L)

    /** ¿Hay que preparar ya el auxiliar? */
    fun shouldPrepare(positionMs: Long, durationMs: Long, fadeMs: Long): Boolean =
        positionMs >= startAt(durationMs, fadeMs) - PREPARE_LEAD_MS && positionMs < startAt(durationMs, fadeMs) + START_GRACE_MS

    /**
     * Curvas de igual potencia: a mitad de la mezcla las dos suenan a ~71 % y el volumen total
     * percibido se mantiene (con curvas lineales se nota un bache en medio).
     * [progress] de 0 a 1. Devuelve (entrante, saliente).
     */
    fun gains(progress: Float): Pair<Float, Float> {
        val t = progress.coerceIn(0f, 1f)
        val angle = t * (PI / 2).toFloat()
        return sin(angle) to cos(angle)
    }
}
