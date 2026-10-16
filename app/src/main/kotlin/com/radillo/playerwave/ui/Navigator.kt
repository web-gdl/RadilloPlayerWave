package com.radillo.playerwave.ui

import androidx.fragment.app.Fragment

/** Destinos de primer nivel (barra inferior o raíl lateral). */
enum class Tab { LIBRARY, NOW_PLAYING, VISUALIZER }

/**
 * Navegación mínima entre fragments, implementada por la actividad: sin biblioteca de navegación.
 * `push` apila un destino (Atrás vuelve), `openTab` limpia la pila y muestra un destino raíz.
 */
interface Navigator {
    fun openTab(tab: Tab)
    fun push(fragment: Fragment)
    fun back()
}

/** Fragments que piden ocultar la navegación y/o el mini reproductor (visualizador inmersivo, etc.). */
interface ChromeMode {
    val hidesNavigation: Boolean get() = false
    val hidesMiniPlayer: Boolean get() = false
}

val Fragment.navigator: Navigator get() = requireActivity() as Navigator
