package com.radillo.playerwave.ui.effects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radillo.playerwave.data.settings.AudioEffectsSettingsRepository
import com.radillo.playerwave.player.audiofx.AudioEffectsController
import com.radillo.playerwave.player.audiofx.AudioEffectsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Ecualizador: los cambios se aplican al instante en [AudioEffectsController] y se persisten
 * al soltar el control (no en cada movimiento del deslizador).
 */
@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val controller: AudioEffectsController,
    private val settings: AudioEffectsSettingsRepository,
) : ViewModel() {

    val state: StateFlow<AudioEffectsState> = controller.state

    fun setEnabled(enabled: Boolean) {
        controller.setEnabled(enabled)
        persist()
    }

    fun previewBandLevel(band: Int, levelMb: Int) = controller.setBandLevel(band, levelMb)

    fun usePreset(index: Int) {
        controller.usePreset(index)
        persist()
    }

    fun previewBassBoost(strength: Int) = controller.setBassBoost(strength)

    fun reset() {
        state.value.bands.forEach { controller.setBandLevel(it.index, 0) }
        controller.setBassBoost(0)
        persist()
    }

    fun persist() {
        val current = controller.currentSettings()
        viewModelScope.launch { settings.update { current } }
    }
}
