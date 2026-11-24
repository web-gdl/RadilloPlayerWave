package com.radillo.playerwave.ui.visualizer

import androidx.annotation.StringRes
import com.radillo.playerwave.R
import com.radillo.playerwave.visualizer.scenes.ModernBarsScene
import com.radillo.playerwave.visualizer.scenes.OscilloscopeScene
import com.radillo.playerwave.visualizer.scenes.RadialSpectrumScene
import com.radillo.playerwave.visualizer.scenes.SpectrumBarsScene
import com.radillo.playerwave.visualizer.scenes.TerrainScene

@StringRes
fun sceneLabelRes(id: String): Int = when (id) {
    SpectrumBarsScene.ID -> R.string.scene_spectrum
    ModernBarsScene.ID -> R.string.scene_modern_bars
    RadialSpectrumScene.ID -> R.string.scene_radial
    OscilloscopeScene.ID -> R.string.scene_oscilloscope
    TerrainScene.ID -> R.string.scene_terrain
    else -> R.string.scene_unknown
}

@StringRes
fun paletteLabelRes(id: String): Int = when (id) {
    "aurora" -> R.string.palette_aurora
    "ember" -> R.string.palette_ember
    "ocean" -> R.string.palette_ocean
    "neon" -> R.string.palette_neon
    "mono" -> R.string.palette_mono
    else -> R.string.scene_unknown
}
