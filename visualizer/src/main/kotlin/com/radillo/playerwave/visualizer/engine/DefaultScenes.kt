package com.radillo.playerwave.visualizer.engine

import com.radillo.playerwave.visualizer.scenes.OscilloscopeScene
import com.radillo.playerwave.visualizer.scenes.RingsScene
import com.radillo.playerwave.visualizer.scenes.SpectrumBarsScene

/** Escenas incluidas en la app. Añadir una escena nueva es añadir una línea aquí. */
object DefaultScenes {
    const val DEFAULT_ID = SpectrumBarsScene.ID

    fun registry(): SceneRegistry = SceneRegistry(
        listOf(
            SpectrumBarsScene.ID to SceneFactory { SpectrumBarsScene() },
            OscilloscopeScene.ID to SceneFactory { OscilloscopeScene() },
            RingsScene.ID to SceneFactory { RingsScene() },
        ),
    )
}
