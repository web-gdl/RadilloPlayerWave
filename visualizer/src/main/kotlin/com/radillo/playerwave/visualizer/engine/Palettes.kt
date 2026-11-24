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

    /**
     * Paleta «Portada»: sigue el color de la carátula, el mismo que pinta los botones del
     * reproductor. En los ajustes se guarda como [COVER_ID]; al motor llega como
     * `cover-RRGGBB` ([coverId]) con el color de la canción actual. Sin color todavía, Aurora.
     */
    const val COVER_ID = "cover"
    private const val COVER_PREFIX = "cover-"

    /** Id de motor para la paleta de portada con el color [accent]. */
    fun coverId(accent: Int): String = COVER_PREFIX + "%06X".format(accent and 0xFFFFFF)

    private var lastCover: VisualPalette? = null

    fun byId(id: String?): VisualPalette {
        if (id != null && id.startsWith(COVER_PREFIX)) {
            lastCover?.let { if (it.id == id) return it }
            val rgb = id.removePrefix(COVER_PREFIX).toIntOrNull(16) ?: return AURORA
            return fromAccent(0xFF000000.toInt() or rgb).also { lastCover = it }
        }
        return all.firstOrNull { it.id == id } ?: AURORA
    }

    /**
     * Paleta armónica a partir del color de la carátula: graves en el tono de la portada algo más
     * profundo, el color tal cual, un vecino más claro y un tono casi blanco para los agudos. El
     * color se aviva si es muy apagado u oscuro para que se vea sobre el fondo negro; el fondo es
     * un negro teñido del mismo tono.
     */
    fun fromAccent(accent: Int): VisualPalette {
        val hsv = Hsv.of(accent)
        val h = hsv[0]
        val s = hsv[1].coerceAtLeast(0.45f)
        val v = hsv[2].coerceAtLeast(0.85f)
        val base = Hsv.toArgb(h, s, v)
        return VisualPalette(
            id = coverId(accent),
            displayName = "Portada",
            background = Hsv.toArgb(h, (s * 0.6f).coerceAtMost(0.6f), 0.06f),
            colors = intArrayOf(
                Hsv.toArgb(h - 14f, (s * 1.1f).coerceAtMost(1f), v * 0.82f),
                base,
                Hsv.toArgb(h + 16f, s * 0.75f, 1f),
                Hsv.toArgb(h + 28f, s * 0.35f, 1f),
            ),
            accent = Hsv.toArgb(h, s * 0.15f, 1f),
        )
    }
}

/** HSV ↔ ARGB en Kotlin puro (sin `android.graphics.Color`, para poder probarlo en la JVM). */
object Hsv {
    fun of(color: Int): FloatArray {
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val h = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        return floatArrayOf((h + 360f) % 360f, if (max == 0f) 0f else d / max, max)
    }

    fun toArgb(hue: Float, saturation: Float, value: Float): Int {
        val h = ((hue % 360f) + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = v - c
        val (r, g, b) = when ((h / 60f).toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(f: Float) = ((f + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }
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
