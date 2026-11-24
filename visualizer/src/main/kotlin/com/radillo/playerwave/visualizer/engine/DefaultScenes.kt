package com.radillo.playerwave.visualizer.engine

import com.radillo.playerwave.visualizer.scenes.ModernBarsScene
import com.radillo.playerwave.visualizer.scenes.OscilloscopeScene
import com.radillo.playerwave.visualizer.scenes.RadialSpectrumScene
import com.radillo.playerwave.visualizer.scenes.SpectrumBarsScene
import com.radillo.playerwave.visualizer.scenes.TerrainScene

/**
 * Escenas incluidas en la app, en el orden en que se recorren (dos toques en la portada pasan a la
 * siguiente). Añadir una escena es añadir una línea aquí. Si la escena guardada ya no existe (las
 * de versiones anteriores: anillos, partículas, túnel, campo de líneas, ambiente), se usa
 * [DEFAULT_ID].
 */
object DefaultScenes {
    const val DEFAULT_ID = SpectrumBarsScene.ID

    fun registry(): SceneRegistry = SceneRegistry(
        listOf(
            SpectrumBarsScene.ID to SceneFactory { SpectrumBarsScene() },     // 1 Barras LED
            ModernBarsScene.ID to SceneFactory { ModernBarsScene() },         // 2 Barras modernas
            RadialSpectrumScene.ID to SceneFactory { RadialSpectrumScene() }, // 3 Radial
            OscilloscopeScene.ID to SceneFactory { OscilloscopeScene() },     // 4 Onda y osciloscopio
            TerrainScene.ID to SceneFactory { TerrainScene() },               // 5 Terreno synthwave
        ),
    )
}
