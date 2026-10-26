package com.radillo.playerwave.core.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Preferencias generales de la aplicación (SharedPreferences). */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Tiñe el acento de la interfaz con el color dominante de la carátula en reproducción. */
    val artworkColors: Boolean = true,
    /**
     * Fuerza la orientación horizontal (pantallas de coche). `null` = según el dispositivo
     * (horizontal fija si no hay acelerómetro o el sistema está en modo coche).
     */
    val lockLandscape: Boolean? = null,
)

