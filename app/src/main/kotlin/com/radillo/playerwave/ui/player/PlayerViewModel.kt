package com.radillo.playerwave.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radillo.playerwave.core.ui.ArtworkAccentExtractor
import com.radillo.playerwave.data.collections.PlaylistRepository
import com.radillo.playerwave.data.settings.AppSettingsRepository
import com.radillo.playerwave.player.engine.PlaybackController
import com.radillo.playerwave.player.engine.PlaybackState
import com.radillo.playerwave.player.engine.RepeatMode
import com.radillo.playerwave.player.engine.SleepTimer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val playback: PlaybackController,
    private val playlists: PlaylistRepository,
    private val sleepTimer: SleepTimer,
    appSettings: AppSettingsRepository,
) : ViewModel() {

    private val sharing = SharingStarted.WhileSubscribed(5_000)
    private val accentExtractor = ArtworkAccentExtractor(context)

    val state: StateFlow<PlaybackState> = playback.state

    val isFavorite: StateFlow<Boolean> = combine(playback.state, playlists.favoriteIds) { s, favs ->
        s.currentTrack?.id?.let { it in favs } ?: false
    }.stateIn(viewModelScope, sharing, false)

    val sleepRemainingMs: StateFlow<Long?> = sleepTimer.remainingMs

    /** Color dominante de la carátula actual, si el ajuste está activo y hay carátula. */
    val accent: StateFlow<Int?> = combine(
        playback.state.map { it.currentTrack?.albumArtUri }.distinctUntilChanged(),
        appSettings.settings.map { it.artworkColors }.distinctUntilChanged(),
    ) { uri, enabled -> if (enabled) uri else null }
        .mapLatest { uri -> uri?.let { accentExtractor.accentFor(it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun togglePlayPause() = playback.togglePlayPause()
    fun next() = playback.next()
    fun previous() = playback.previous()
    fun seekTo(positionMs: Long) = playback.seekTo(positionMs)
    fun toggleShuffle() = playback.setShuffle(!state.value.shuffleEnabled)

    fun cycleRepeat() {
        val next = when (state.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        playback.setRepeatMode(next)
    }

    fun toggleFavorite() {
        val id = state.value.currentTrack?.id ?: return
        viewModelScope.launch { playlists.toggleFavorite(id) }
    }

    fun startSleepTimer(minutes: Int) = sleepTimer.start(minutes * 60_000L)
    fun cancelSleepTimer() = sleepTimer.cancel()

    fun skipToQueueItem(index: Int) = playback.skipToQueueItem(index)
    fun removeFromQueue(index: Int) = playback.removeFromQueue(index)
}
