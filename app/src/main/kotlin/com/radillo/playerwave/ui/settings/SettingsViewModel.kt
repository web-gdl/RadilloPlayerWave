package com.radillo.playerwave.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radillo.playerwave.BuildConfig
import com.radillo.playerwave.R
import com.radillo.playerwave.core.device.DeviceProfile
import com.radillo.playerwave.core.model.AppSettings
import com.radillo.playerwave.core.model.ThemeMode
import com.radillo.playerwave.data.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: AppSettingsRepository,
    val profile: DeviceProfile,
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, repository.snapshot())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.update { it.copy(themeMode = mode) } }
    }

    fun setArtworkColors(enabled: Boolean) {
        viewModelScope.launch { repository.update { it.copy(artworkColors = enabled) } }
    }

    fun setLockLandscape(enabled: Boolean) {
        viewModelScope.launch { repository.update { it.copy(lockLandscape = enabled) } }
    }
}
