package com.radillo.playerwave.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

private val DarkColors = darkColorScheme(
    primary = Cyan80,
    onPrimary = Night0,
    primaryContainer = Cyan40,
    onPrimaryContainer = Mist90,
    secondary = Magenta80,
    onSecondary = Night0,
    secondaryContainer = Magenta40,
    onSecondaryContainer = Mist90,
    tertiary = Amber80,
    onTertiary = Night0,
    tertiaryContainer = Amber40,
    onTertiaryContainer = Mist90,
    background = Night1,
    onBackground = Mist90,
    surface = Night1,
    onSurface = Mist90,
    surfaceVariant = Night3,
    onSurfaceVariant = Mist70,
    surfaceContainerLowest = Night0,
    surfaceContainerLow = Night1,
    surfaceContainer = Night2,
    surfaceContainerHigh = Night3,
    surfaceContainerHighest = Night4,
    outline = Mist50,
    outlineVariant = Night4,
    error = ErrorRed,
    onError = Night0,
)

private val LightColors = lightColorScheme(
    primary = Cyan40,
    onPrimary = Day0,
    primaryContainer = Color_CyanContainerLight,
    onPrimaryContainer = Ink90,
    secondary = Magenta40,
    onSecondary = Day0,
    secondaryContainer = Color_MagentaContainerLight,
    onSecondaryContainer = Ink90,
    tertiary = Amber40,
    onTertiary = Day0,
    tertiaryContainer = Color_AmberContainerLight,
    onTertiaryContainer = Ink90,
    background = Day1,
    onBackground = Ink90,
    surface = Day1,
    onSurface = Ink90,
    surfaceVariant = Day3,
    onSurfaceVariant = Ink70,
    surfaceContainerLowest = Day0,
    surfaceContainerLow = Day1,
    surfaceContainer = Day2,
    surfaceContainerHigh = Day3,
    surfaceContainerHighest = Day3,
    outline = Ink50,
    outlineVariant = Day3,
    error = ErrorRedDark,
    onError = Day0,
)

/**
 * Tema de la aplicación. No se usa color dinámico (Material You) a propósito: la identidad
 * visual debe ser estable y coherente con las escenas del visualizador.
 *
 * @param accent color de acento opcional (p. ej. el dominante de la carátula en reproducción);
 * se ajusta en saturación y luminosidad para mantener el contraste y sustituye al primario.
 */
@Composable
fun RadilloTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: Color? = null,
    content: @Composable () -> Unit,
) {
    val base = if (darkTheme) DarkColors else LightColors
    val scheme = remember(darkTheme, accent) { if (accent == null) base else base.withAccent(accent, darkTheme) }
    MaterialTheme(
        colorScheme = scheme,
        typography = RadilloTypography,
        shapes = RadilloShapes,
        content = content,
    )
}

private fun ColorScheme.withAccent(accent: Color, dark: Boolean): ColorScheme {
    val primary = adjustForTheme(accent, dark)
    val container = if (dark) primary.copy(alpha = 1f).darken(0.45f) else primary.lighten(0.55f)
    return copy(
        primary = primary,
        onPrimary = if (dark) Night0 else Day0,
        primaryContainer = container,
        onPrimaryContainer = if (dark) Mist90 else Ink90,
    )
}

/** Fuerza saturación y luminosidad a un rango legible sobre el fondo del tema. */
private fun adjustForTheme(color: Color, dark: Boolean): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[1] = hsv[1].coerceIn(0.35f, 0.85f)
    hsv[2] = if (dark) hsv[2].coerceIn(0.72f, 0.98f) else hsv[2].coerceIn(0.35f, 0.62f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun Color.darken(amount: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[2] = (hsv[2] * (1f - amount)).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun Color.lighten(amount: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[1] = (hsv[1] * (1f - amount)).coerceIn(0f, 1f)
    hsv[2] = (hsv[2] + (1f - hsv[2]) * amount).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}
