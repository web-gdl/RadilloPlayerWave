package com.radillo.playerwave.ui.visualizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radillo.playerwave.core.device.DeviceProfile
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
    val profile: DeviceProfile,
) : ViewModel() {

    val settings: StateFlow<VisualizerSettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, VisualizerSettings())
    val playbackState: StateFlow<PlaybackState> = playback.state
    val dataState: StateFlow<DataState> = engine.dataState
    /** Factor de calidad vigente (1 = completo) decidido por el gobernador. */
    val quality: StateFlow<Float> = engine.quality
    val sceneIds: List<String> = engine.sceneIds
    /** Paletas a elegir: primero «Portada» (color de la carátula), luego las fijas. */
    val paletteIds: List<String> = listOf(Palettes.COVER_ID) + Palettes.all.map(VisualPalette::id)

    init {
        // Perfil del dispositivo: menos densidad y calidad mínima más baja en gama baja.
        engine.setDeviceScale(profile.visualizerDensityScale)
        engine.setMinQuality(profile.minQuality)
        viewModelScope.launch { settingsRepository.settings.collect { applyToEngine(it) } }
    }

    /** Tope de fps efectivo (el elegido, o el del perfil si está en automático). */
    fun effectiveMaxFps(settings: VisualizerSettings): Int = if (settings.maxFps > 0) settings.maxFps else profile.defaultMaxFps

    /** Aplica los ajustes al motor sin persistirlos (vista previa mientras se arrastra un control). */
    fun preview(settings: VisualizerSettings) = applyToEngine(settings)

    /**
     * Color de la portada actual, el mismo de los botones del reproductor. Con la paleta
     * «Portada» los visualizadores lo siguen. Se guarda a nivel de proceso para que la pantalla
     * completa lo tenga desde el primer frame.
     */
    fun setCoverAccent(color: Int) {
        if (coverAccent == color) return
        coverAccent = color
        applyToEngine(settings.value)
    }

    fun selectScene(id: String) = commit { it.copy(sceneId = id) }
    fun selectPalette(id: String) = commit { it.copy(paletteId = id) }
    fun setKeepScreenOn(enabled: Boolean) = commit { it.copy(keepScreenOn = enabled) }
    fun setAutoQuality(enabled: Boolean) = commit { it.copy(autoQuality = enabled) }
    fun setMaxFps(fps: Int) = commit { it.copy(maxFps = fps) }
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
                paletteId = resolvePalette(s.paletteId),
            ),
        )
        engine.setSyncOffsetMs(s.syncOffsetMs)
        engine.setAutoQuality(s.autoQuality)
        engine.setMaxFps(effectiveMaxFps(s))
    }

    companion object {
        @Volatile
        private var coverAccent: Int? = null

        /** «cover» → id de motor con el color actual (Aurora mientras no hay color). */
        fun resolvePalette(paletteId: String): String =
            if (paletteId == Palettes.COVER_ID) coverAccent?.let(Palettes::coverId) ?: Palettes.AURORA.id else paletteId
    }
}
