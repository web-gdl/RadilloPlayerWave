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

/**
 * Ajustes del ecualizador y efectos. Las bandas se guardan en milibelios (como las expone
 * `android.media.audiofx.Equalizer`); una lista vacía significa "plano".
 */
data class AudioEffectsSettings(
    val enabled: Boolean = false,
    val bandLevelsMb: List<Int> = emptyList(),
    /** Índice del preajuste de la plataforma aplicado, o -1 si los niveles son manuales. */
    val presetIndex: Int = -1,
    /** Refuerzo de graves, 0..1000. */
    val bassBoostStrength: Int = 0,
)
