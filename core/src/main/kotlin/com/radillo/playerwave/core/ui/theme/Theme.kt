package com.radillo.playerwave.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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
 * Tema de la aplicación. No se usa color dinámico (Material You) a propósito:
 * la identidad visual debe ser estable y coherente con las escenas del visualizador.
 */
@Composable
fun RadilloTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = RadilloTypography,
        shapes = RadilloShapes,
        content = content,
    )
}
