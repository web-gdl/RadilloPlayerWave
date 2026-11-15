package com.radillo.playerwave.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.SharedPreferences
import com.radillo.playerwave.core.settings.PlaybackSettings
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.radillo.playerwave.data.waveform.WaveformRepository
import kotlinx.coroutines.flow.transformLatest
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val playback: PlaybackController,
    private val playlists: PlaylistRepository,
    private val sleepTimer: SleepTimer,
    appSettings: AppSettingsRepository,
    private val waveforms: WaveformRepository,
) : ViewModel() {

    private val sharing = SharingStarted.WhileSubscribed(5_000)
    private val accentExtractor = ArtworkAccentExtractor(context)

    val state: StateFlow<PlaybackState> = playback.state

    // Solo la canción actual: el estado cambia con cada tic de progreso y no hace falta recalcular.
    val isFavorite: StateFlow<Boolean> = combine(
        playback.state.map { it.currentTrack?.id }.distinctUntilChanged(),
        playlists.favoriteIds,
    ) { id, favs -> id != null && id in favs }
        .stateIn(viewModelScope, sharing, false)

    val sleepRemainingMs: StateFlow<Long?> = sleepTimer.remainingMs

    private val appContext = context.applicationContext
    private val playbackPrefs = PlaybackSettings.prefs(appContext)
    private val _crossfadeSeconds = MutableStateFlow(PlaybackSettings.crossfadeSeconds(appContext))

    /** Segundos de crossfade (0 = apagado); sigue también los cambios hechos en Ajustes. */
    val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds

    private val crossfadeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PlaybackSettings.KEY_CROSSFADE_SECONDS) _crossfadeSeconds.value = PlaybackSettings.crossfadeSeconds(appContext)
    }

    init {
        playbackPrefs.registerOnSharedPreferenceChangeListener(crossfadeListener)
    }

    override fun onCleared() {
        playbackPrefs.unregisterOnSharedPreferenceChangeListener(crossfadeListener)
        super.onCleared()
    }

    /** Activa o desactiva el crossfade (recuerda la última duración). Devuelve los segundos puestos. */
    fun toggleCrossfade(): Int = PlaybackSettings.toggleCrossfade(appContext).also { _crossfadeSeconds.value = it }

    /** Color dominante de la carátula actual, si el ajuste está activo y hay carátula. */
    val accent: StateFlow<Int?> = combine(
        playback.state.map { it.currentTrack?.coverUri }.distinctUntilChanged(),
        appSettings.settings.map { it.artworkColors }.distinctUntilChanged(),
    ) { uri, enabled -> if (enabled) uri else null }
        .mapLatest { uri -> uri?.let { accentExtractor.accentFor(it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Onda de sonido de la canción actual para la barra de progreso (`null` mientras se calcula o
     * si no se puede: entonces la barra se dibuja lisa). Solo se calcula con la pantalla a la vista.
     */
    val waveform: StateFlow<ByteArray?> = playback.state
        .map { it.currentTrack }
        .distinctUntilChanged { a, b -> a?.id == b?.id && a?.durationMs == b?.durationMs }
        .transformLatest { track ->
            emit(null)
            if (track != null && track.durationMs > 0) emit(waveforms.load(track.id, track.uri, track.durationMs))
        }
        .stateIn(viewModelScope, sharing, null)

    /** Qué pasó al tocar «Favoritos» en la barra superior. */
    enum class FavoritesResult { NONE, ALREADY_PLAYING, STARTED }

    /**
     * Reproduce la lista de favoritos (en el orden de su pantalla, respetando aleatorio y repetición).
     * Si ya es la cola que está sonando, no la reinicia: [onResult] recibe ALREADY_PLAYING.
     */
    fun playFavorites(onResult: (FavoritesResult) -> Unit) {
        viewModelScope.launch {
            val favorites = playlists.favoriteTracks.first()
            val result = when {
                favorites.isEmpty() -> FavoritesResult.NONE
                state.value.queue.map { it.id }.toSet() == favorites.map { it.id }.toSet() &&
                    state.value.currentTrack != null -> FavoritesResult.ALREADY_PLAYING
                else -> {
                    playback.playQueue(favorites, 0)
                    FavoritesResult.STARTED
                }
            }
            onResult(result)
        }
    }

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
