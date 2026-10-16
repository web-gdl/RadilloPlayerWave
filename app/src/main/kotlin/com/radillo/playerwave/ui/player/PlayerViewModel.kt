package com.radillo.playerwave.ui.player

import androidx.lifecycle.ViewModel
import com.radillo.playerwave.player.engine.PlaybackController
import com.radillo.playerwave.player.engine.PlaybackState
import com.radillo.playerwave.player.engine.RepeatMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playback: PlaybackController,
) : ViewModel() {

    val state: StateFlow<PlaybackState> = playback.state

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

    fun skipToQueueItem(index: Int) = playback.skipToQueueItem(index)
    fun removeFromQueue(index: Int) = playback.removeFromQueue(index)
}
