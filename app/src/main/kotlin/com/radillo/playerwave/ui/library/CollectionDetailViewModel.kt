package com.radillo.playerwave.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radillo.playerwave.core.model.Track
import com.radillo.playerwave.data.library.LibraryRepository
import com.radillo.playerwave.player.engine.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Tipo de colección mostrada en la pantalla de detalle. Los valores son parte de la ruta de navegación. */
enum class CollectionKind { ALBUM, ARTIST, FOLDER }

@HiltViewModel
class CollectionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: LibraryRepository,
    private val playback: PlaybackController,
) : ViewModel() {

    val kind: CollectionKind = CollectionKind.valueOf(checkNotNull(savedStateHandle.get<String>(ARG_KIND)))
    val title: String = savedStateHandle.get<String>(ARG_TITLE).orEmpty()
    private val key: String = checkNotNull(savedStateHandle.get<String>(ARG_KEY))

    val tracks: StateFlow<List<Track>> = when (kind) {
        CollectionKind.ALBUM -> repository.tracksOfAlbum(key.toLong())
        CollectionKind.ARTIST -> repository.tracksOfArtist(key.toLong())
        CollectionKind.FOLDER -> repository.tracksOfFolder(key)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val nowPlayingId: StateFlow<Long?> =
        playback.state.map { it.currentTrack?.id }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun play(index: Int) = playback.playQueue(tracks.value, index)
    fun shuffle() {
        val list = tracks.value
        if (list.isEmpty()) return
        playback.setShuffle(true)
        playback.playQueue(list, list.indices.random())
    }
    companion object {
        const val ARG_KIND = "kind"
        const val ARG_KEY = "key"
        const val ARG_TITLE = "title"
    }
}
