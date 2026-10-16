package com.radillo.playerwave.visualizer.engine

/**
 * Paleta de una escena, en colores ARGB empaquetados (`Int`), sin tipos de Compose.
 * `colors` va de grave a agudo (o de centro a borde, según la escena); `accent` se usa para
 * pulsos y destellos; `background` es el fondo del lienzo.
 */
class VisualPalette(
    val id: String,
    val displayName: String,
    val background: Int,
    val colors: IntArray,
    val accent: Int,
)

object Palettes {
    val AURORA = VisualPalette(
        id = "aurora",
        displayName = "Aurora",
        background = 0xFF070A12.toInt(),
        colors = intArrayOf(0xFF2BD4FF.toInt(), 0xFF5EE7FF.toInt(), 0xFF8C7BFF.toInt(), 0xFFFF7BBF.toInt()),
        accent = 0xFFE6FBFF.toInt(),
    )
    val EMBER = VisualPalette(
        id = "ember",
        displayName = "Brasa",
        background = 0xFF0E0705.toInt(),
        colors = intArrayOf(0xFFFF4D2E.toInt(), 0xFFFF8A3D.toInt(), 0xFFFFC46B.toInt(), 0xFFFFF1B8.toInt()),
        accent = 0xFFFFF6DC.toInt(),
    )
    val OCEAN = VisualPalette(
        id = "ocean",
        displayName = "Océano",
        background = 0xFF040A14.toInt(),
        colors = intArrayOf(0xFF0E5CFF.toInt(), 0xFF1FB2FF.toInt(), 0xFF3DE6D0.toInt(), 0xFFB8FFF0.toInt()),
        accent = 0xFFE0FFFB.toInt(),
    )
    val NEON = VisualPalette(
        id = "neon",
        displayName = "Neón",
        background = 0xFF07090B.toInt(),
        colors = intArrayOf(0xFF39FF88.toInt(), 0xFFB6FF3C.toInt(), 0xFFFFE93C.toInt(), 0xFFFF6BD6.toInt()),
        accent = 0xFFF2FFF5.toInt(),
    )
    val MONO = VisualPalette(
        id = "mono",
        displayName = "Mono",
        background = 0xFF0B0B0D.toInt(),
        colors = intArrayOf(0xFF9AA3B2.toInt(), 0xFFC7CDD8.toInt(), 0xFFE6EAF2.toInt(), 0xFFFFFFFF.toInt()),
        accent = 0xFFFFFFFF.toInt(),
    )

    val all: List<VisualPalette> = listOf(AURORA, EMBER, OCEAN, NEON, MONO)

    fun byId(id: String?): VisualPalette = all.firstOrNull { it.id == id } ?: AURORA
}

/** Utilidades de color ARGB sin asignaciones. */
object Argb {
    const val TRANSPARENT = 0

    /** Sustituye el canal alfa de `color` por `alpha` ∈ [0, 1]. */
    fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return (color and 0x00FFFFFF) or (a shl 24)
    }

    /** Interpolación lineal canal a canal. */
    fun lerp(from: Int, to: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        val a = ((from ushr 24) + (((to ushr 24) - (from ushr 24)) * k)).toInt()
        val r = (((from shr 16) and 0xFF) + ((((to shr 16) and 0xFF) - ((from shr 16) and 0xFF)) * k)).toInt()
        val g = (((from shr 8) and 0xFF) + ((((to shr 8) and 0xFF) - ((from shr 8) and 0xFF)) * k)).toInt()
        val b = ((from and 0xFF) + (((to and 0xFF) - (from and 0xFF)) * k)).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
