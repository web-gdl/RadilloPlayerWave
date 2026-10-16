package com.radillo.playerwave.ui.visualizer

import androidx.annotation.StringRes
import com.radillo.playerwave.R
import com.radillo.playerwave.visualizer.scenes.AmbientScene
import com.radillo.playerwave.visualizer.scenes.LineFieldScene
import com.radillo.playerwave.visualizer.scenes.OscilloscopeScene
import com.radillo.playerwave.visualizer.scenes.ParticlesScene
import com.radillo.playerwave.visualizer.scenes.RingsScene
import com.radillo.playerwave.visualizer.scenes.SpectrumBarsScene
import com.radillo.playerwave.visualizer.scenes.TunnelScene

@StringRes
fun sceneLabelRes(id: String): Int = when (id) {
    SpectrumBarsScene.ID -> R.string.scene_spectrum
    OscilloscopeScene.ID -> R.string.scene_oscilloscope
    RingsScene.ID -> R.string.scene_rings
    ParticlesScene.ID -> R.string.scene_particles
    TunnelScene.ID -> R.string.scene_tunnel
    LineFieldScene.ID -> R.string.scene_lines
    AmbientScene.ID -> R.string.scene_ambient
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
