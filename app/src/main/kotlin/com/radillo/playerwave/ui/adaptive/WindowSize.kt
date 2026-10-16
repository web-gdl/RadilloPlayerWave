package com.radillo.playerwave.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/** Umbral de ancho (clase de tamaño "medium" de Material) a partir del cual se usa la disposición ancha. */
private val WideThreshold = 600.dp

/**
 * `true` cuando la ventana es lo bastante ancha para una disposición de dos columnas o un
 * raíl de navegación: tabletas, plegables abiertos y teléfonos en horizontal.
 */
@Composable
fun isWideScreen(): Boolean {
    val widthPx = LocalWindowInfo.current.containerSize.width
    val widthDp = with(LocalDensity.current) { widthPx.toDp() }
    return widthDp >= WideThreshold
}
