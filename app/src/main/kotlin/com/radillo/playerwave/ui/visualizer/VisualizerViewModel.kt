package com.radillo.playerwave.ui.visualizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radillo.playerwave.core.model.VisualizerSettings
import com.radillo.playerwave.data.settings.VisualizerSettingsRepository
import com.radillo.playerwave.player.engine.PlaybackController
import com.radillo.playerwave.player.engine.PlaybackState
import com.radillo.playerwave.visualizer.engine.DataState
import com.radillo.playerwave.visualizer.engine.DefaultScenes
import com.radillo.playerwave.visualizer.engine.Palettes
import com.radillo.playerwave.visualizer.engine.SceneParams
import com.radillo.playerwave.visualizer.engine.VisualPalette
import com.radillo.playerwave.visualizer.engine.VisualizerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VisualizerViewModel @Inject constructor(
    val engine: VisualizerEngine,
    private val settingsRepository: VisualizerSettingsRepository,
    private val playback: PlaybackController,
) : ViewModel() {

    val settings: StateFlow<VisualizerSettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, VisualizerSettings())
    val playbackState: StateFlow<PlaybackState> = playback.state
    val dataState: StateFlow<DataState> = engine.dataState
    val sceneIds: List<String> = engine.sceneIds
    val palettes: List<VisualPalette> = Palettes.all

    init {
        viewModelScope.launch { settingsRepository.settings.collect { applyToEngine(it) } }
    }

    /** Aplica los ajustes al motor sin persistirlos (vista previa mientras se arrastra un control). */
    fun preview(settings: VisualizerSettings) = applyToEngine(settings)

    fun selectScene(id: String) = commit { it.copy(sceneId = id) }
    fun selectPalette(id: String) = commit { it.copy(paletteId = id) }
    fun setKeepScreenOn(enabled: Boolean) = commit { it.copy(keepScreenOn = enabled) }
    fun resetTuning() = commit {
        it.copy(sensitivity = 1f, intensity = 1f, speed = 1f, density = 1f, syncOffsetMs = 0)
    }

    /** Persiste una transformación de los ajustes. */
    fun commit(transform: (VisualizerSettings) -> VisualizerSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun togglePlayPause() = playback.togglePlayPause()
    fun next() = playback.next()
    fun previous() = playback.previous()

    private fun applyToEngine(s: VisualizerSettings) {
        val sceneId = if (s.sceneId in sceneIds) s.sceneId else DefaultScenes.DEFAULT_ID
        engine.setScene(sceneId)
        engine.setParams(
            SceneParams(
                sensitivity = s.sensitivity,
                intensity = s.intensity,
                speed = s.speed,
                density = s.density,
                paletteId = s.paletteId,
            ),
        )
        engine.setSyncOffsetMs(s.syncOffsetMs)
    }
}
