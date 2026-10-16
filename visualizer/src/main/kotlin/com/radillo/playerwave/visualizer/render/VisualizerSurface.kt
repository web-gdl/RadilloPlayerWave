package com.radillo.playerwave.visualizer.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.radillo.playerwave.visualizer.engine.VisualizerEngine

/**
 * Lienzo que renderiza el [VisualizerEngine] a la cadencia de la pantalla.
 *
 * - El bucle usa `withFrameNanos`: Compose lo pausa cuando la ventana no es visible.
 * - El análisis de audio solo corre mientras la superficie está en primer plano
 *   (`attach` en RESUMED, `detach` en PAUSED), para no gastar batería en segundo plano.
 */
@Composable
fun VisualizerSurface(
    engine: VisualizerEngine,
    modifier: Modifier = Modifier,
) {
    val frameTick = remember { mutableLongStateOf(0L) }

    LifecycleResumeEffect(engine) {
        engine.attach()
        onPauseOrDispose { engine.detach() }
    }

    LaunchedEffect(engine) {
        while (true) {
            withFrameNanos { now ->
                engine.onFrame(now)
                frameTick.longValue = now
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { engine.onResize(it.width, it.height) },
    ) {
        // Leer el tick dentro del lambda de dibujo invalida solo la fase de dibujo en cada frame.
        @Suppress("UNUSED_VARIABLE")
        val tick = frameTick.longValue
        engine.draw(this)
    }
}
