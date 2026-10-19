package com.radillo.playerwave.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radillo.playerwave.core.device.DeviceProfile
import com.radillo.playerwave.core.model.AppSettings
import com.radillo.playerwave.core.model.ThemeMode
import com.radillo.playerwave.data.settings.AppSettingsRepository
import android.content.Context
import com.radillo.playerwave.core.settings.PlaybackSettings
import com.radillo.playerwave.data.library.LibraryRepository
import com.radillo.playerwave.data.library.ScanResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AppSettingsRepository,
    private val library: LibraryRepository,
    val profile: DeviceProfile,
) : ViewModel() {

    /** «Seguir sonando aunque otra app pida el audio» (lo lee el servicio de reproducción). */
    val keepPlaying = MutableStateFlow(PlaybackSettings.keepPlayingOnFocusLoss(context, profile.fixedLandscape))

    fun setKeepPlaying(enabled: Boolean) {
        PlaybackSettings.setKeepPlayingOnFocusLoss(context, enabled)
        keepPlaying.value = enabled
    }

    /** Segundos de crossfade (0 = apagado); lo lee el servicio de reproducción al vuelo. */
    val crossfadeSeconds = MutableStateFlow(PlaybackSettings.crossfadeSeconds(context))

    fun setCrossfadeSeconds(seconds: Int) {
        PlaybackSettings.setCrossfadeSeconds(context, seconds)
        crossfadeSeconds.value = PlaybackSettings.crossfadeSeconds(context)
    }

    /** Mezclar también al saltar de canción a mano. */
    val crossfadeOnSkip = MutableStateFlow(PlaybackSettings.crossfadeOnSkip(context))

    fun setCrossfadeOnSkip(enabled: Boolean) {
        PlaybackSettings.setCrossfadeOnSkip(context, enabled)
        crossfadeOnSkip.value = enabled
    }

    val isScanning: StateFlow<Boolean> = library.isScanning
    private val _scanResults = MutableSharedFlow<ScanResult>(extraBufferCapacity = 1)
    val scanResults: SharedFlow<ScanResult> = _scanResults

    fun rescan() {
        viewModelScope.launch { _scanResults.emit(library.rescan(force = true)) }
    }

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
