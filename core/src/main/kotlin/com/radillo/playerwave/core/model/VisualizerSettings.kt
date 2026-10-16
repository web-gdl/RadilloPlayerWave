package com.radillo.playerwave.core.model

/** Preferencias del visualizador, persistidas en DataStore. */
data class VisualizerSettings(
    val sceneId: String = "spectrum",
    val sensitivity: Float = 1f,
    val intensity: Float = 1f,
    val speed: Float = 1f,
    val density: Float = 1f,
    val paletteId: String = "aurora",
    /** Desplazamiento manual imagen/sonido en ms (positivo = la imagen va más tarde). */
    val syncOffsetMs: Int = 0,
    val keepScreenOn: Boolean = true,
    /** Gobernador de calidad: reduce la densidad de las escenas si el dispositivo no mantiene la fluidez. */
    val autoQuality: Boolean = true,
) {
    companion object {
        val SENSITIVITY_RANGE = 0.25f..3f
        val INTENSITY_RANGE = 0.2f..2f
        val SPEED_RANGE = 0.25f..3f
        val DENSITY_RANGE = 0.25f..2f
        val SYNC_RANGE_MS = -300..800
    }
}
