package com.radillo.playerwave.visualizer.engine

import com.radillo.playerwave.visualizer.scenes.AmbientScene
import com.radillo.playerwave.visualizer.scenes.LineFieldScene
import com.radillo.playerwave.visualizer.scenes.OscilloscopeScene
import com.radillo.playerwave.visualizer.scenes.ParticlesScene
import com.radillo.playerwave.visualizer.scenes.RingsScene
import com.radillo.playerwave.visualizer.scenes.SpectrumBarsScene
import com.radillo.playerwave.visualizer.scenes.TunnelScene

/** Escenas incluidas en la app. Añadir una escena nueva es añadir una línea aquí. */
object DefaultScenes {
    const val DEFAULT_ID = SpectrumBarsScene.ID

    fun registry(): SceneRegistry = SceneRegistry(
        listOf(
            SpectrumBarsScene.ID to SceneFactory { SpectrumBarsScene() },   // A
            OscilloscopeScene.ID to SceneFactory { OscilloscopeScene() },   // B
            RingsScene.ID to SceneFactory { RingsScene() },                 // C
            ParticlesScene.ID to SceneFactory { ParticlesScene() },         // D
            TunnelScene.ID to SceneFactory { TunnelScene() },               // E
            LineFieldScene.ID to SceneFactory { LineFieldScene() },         // G
            AmbientScene.ID to SceneFactory { AmbientScene() },             // H
            // F (terreno 3D, OpenGL ES) llega en la Fase 5.
        ),
    )
}
