package com.radillo.playerwave.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.ui.graphics.vector.ImageVector
import com.radillo.playerwave.R

/**
 * Destinos de primer nivel. Las rutas son cadenas estables; los destinos anidados
 * (álbum, artista, carpeta, lista) se añaden en la Fase 1 sin cambiar estos.
 */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    LIBRARY(
        route = "library",
        labelRes = R.string.nav_library,
        icon = Icons.Outlined.LibraryMusic,
        selectedIcon = Icons.Rounded.LibraryMusic,
    ),
    NOW_PLAYING(
        route = "now_playing",
        labelRes = R.string.nav_now_playing,
        icon = Icons.Outlined.PlayCircle,
        selectedIcon = Icons.Rounded.PlayCircle,
    ),
    VISUALIZER(
        route = "visualizer",
        labelRes = R.string.nav_visualizer,
        icon = Icons.Outlined.GraphicEq,
        selectedIcon = Icons.Rounded.GraphicEq,
    ),
}
