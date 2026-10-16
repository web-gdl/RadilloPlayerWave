package com.radillo.playerwave.visualizer.engine

import androidx.compose.ui.graphics.Color

/**
 * Paleta de una escena. `colors` va de grave a agudo (o de centro a borde, según la escena);
 * `accent` se usa para pulsos y destellos; `background` es el fondo del lienzo.
 */
class VisualPalette(
    val id: String,
    val displayName: String,
    val background: Color,
    val colors: List<Color>,
    val accent: Color,
)

object Palettes {
    val AURORA = VisualPalette(
        id = "aurora",
        displayName = "Aurora",
        background = Color(0xFF070A12),
        colors = listOf(Color(0xFF2BD4FF), Color(0xFF5EE7FF), Color(0xFF8C7BFF), Color(0xFFFF7BBF)),
        accent = Color(0xFFE6FBFF),
    )
    val EMBER = VisualPalette(
        id = "ember",
        displayName = "Brasa",
        background = Color(0xFF0E0705),
        colors = listOf(Color(0xFFFF4D2E), Color(0xFFFF8A3D), Color(0xFFFFC46B), Color(0xFFFFF1B8)),
        accent = Color(0xFFFFF6DC),
    )
    val OCEAN = VisualPalette(
        id = "ocean",
        displayName = "Océano",
        background = Color(0xFF040A14),
        colors = listOf(Color(0xFF0E5CFF), Color(0xFF1FB2FF), Color(0xFF3DE6D0), Color(0xFFB8FFF0)),
        accent = Color(0xFFE0FFFB),
    )
    val NEON = VisualPalette(
        id = "neon",
        displayName = "Neón",
        background = Color(0xFF07090B),
        colors = listOf(Color(0xFF39FF88), Color(0xFFB6FF3C), Color(0xFFFFE93C), Color(0xFFFF6BD6)),
        accent = Color(0xFFF2FFF5),
    )
    val MONO = VisualPalette(
        id = "mono",
        displayName = "Mono",
        background = Color(0xFF0B0B0D),
        colors = listOf(Color(0xFF9AA3B2), Color(0xFFC7CDD8), Color(0xFFE6EAF2), Color(0xFFFFFFFF)),
        accent = Color(0xFFFFFFFF),
    )

    val all: List<VisualPalette> = listOf(AURORA, EMBER, OCEAN, NEON, MONO)

    fun byId(id: String?): VisualPalette = all.firstOrNull { it.id == id } ?: AURORA
}
