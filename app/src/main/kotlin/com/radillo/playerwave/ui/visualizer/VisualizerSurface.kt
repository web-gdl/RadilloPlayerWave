package com.radillo.playerwave.ui.visualizer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.radillo.playerwave.visualizer.engine.VisualizerEngine
import com.radillo.playerwave.visualizer.render.VisualizerSurfaceView

/**
 * Envuelve [VisualizerSurfaceView] (SurfaceView con hilo de render propio) para la UI Compose
 * actual. El ciclo de vida del análisis y del hilo lo gobierna la propia superficie.
 */
@Composable
fun VisualizerSurface(
    engine: VisualizerEngine,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context -> VisualizerSurfaceView(context).apply { setEngine(engine) } },
        update = { view -> view.setEngine(engine) },
        modifier = modifier,
    )
}
