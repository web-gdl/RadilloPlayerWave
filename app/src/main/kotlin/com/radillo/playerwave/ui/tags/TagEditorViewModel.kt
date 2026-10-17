package com.radillo.playerwave.ui.tags

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radillo.playerwave.core.model.Track
import com.radillo.playerwave.data.library.LibraryRepository
import com.radillo.playerwave.data.tags.CoverChange
import com.radillo.playerwave.data.tags.SaveOutcome
import com.radillo.playerwave.data.tags.TagEdit
import com.radillo.playerwave.data.tags.TagWriter
import com.radillo.playerwave.player.engine.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Estado de la pantalla. `cover` null = mostrar la carátula actual de la app (o el marcador). */
data class EditorState(
    val track: Track? = null,
    val supported: Boolean = true,
    val cover: Bitmap? = null,
    val coverRemoved: Boolean = false,
    val saving: Boolean = false,
    val loaded: Boolean = false,
)

sealed interface EditorEvent {
    data object Saved : EditorEvent
    data object NeedsWritePermission : EditorEvent
    class NeedsConsent(val outcome: SaveOutcome.NeedsConsent) : EditorEvent
    data object NeedsFolderAccess : EditorEvent
    class Failed(val reason: SaveOutcome.Reason) : EditorEvent
    data object ImageFailed : EditorEvent
}

@HiltViewModel
class TagEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val library: LibraryRepository,
    private val writer: TagWriter,
    private val playback: PlaybackController,
) : ViewModel() {

    private val trackId: Long = checkNotNull(savedState.get<Long>(ARG_TRACK_ID))

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state
    private val _events = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<EditorEvent> = _events

    private var coverChange: CoverChange = CoverChange.Keep
    /** Última edición pedida: se reintenta tras conceder un permiso. */
    private var pendingEdit: TagEdit? = null

    val hasCoverChange: Boolean get() = coverChange !is CoverChange.Keep

    init {
        viewModelScope.launch {
            val track = library.track(trackId) ?: playback.state.value.queue.firstOrNull { it.id == trackId }
            if (track == null) {
                _state.value = EditorState(loaded = true, supported = false)
                return@launch
            }
            val info = writer.inspect(track)
            _state.value = EditorState(
                track = track,
                supported = info.supported,
                cover = info.embeddedCover?.let { decode(it) },
                loaded = true,
            )
        }
    }

    fun pickCover(uri: Uri) {
        viewModelScope.launch {
            val bytes = writer.prepareCover(uri)
            if (bytes == null) {
                _events.emit(EditorEvent.ImageFailed)
                return@launch
            }
            coverChange = CoverChange.Replace(bytes, "image/jpeg")
            _state.value = _state.value.copy(cover = decode(bytes), coverRemoved = false)
        }
    }

    fun removeCover() {
        coverChange = CoverChange.Remove
        _state.value = _state.value.copy(cover = null, coverRemoved = true)
    }

    fun save(title: String, artist: String, album: String, year: Int?, trackNumber: Int?) {
        val edit = TagEdit(title, artist, album, year, trackNumber, coverChange)
        pendingEdit = edit
        run(edit, consentGranted = false)
    }

    /** Tras conceder permiso, consentimiento o carpeta. */
    fun retry(consentGranted: Boolean) {
        pendingEdit?.let { run(it, consentGranted) }
    }

    fun folderGranted(treeUri: Uri) {
        writer.rememberFolder(treeUri)
        retry(consentGranted = false)
    }

    fun canPickFolders(): Boolean = writer.canPickFolders()

    private fun run(edit: TagEdit, consentGranted: Boolean) {
        val track = _state.value.track ?: return
        if (_state.value.saving) return
        _state.value = _state.value.copy(saving = true)
        viewModelScope.launch {
            val outcome = writer.save(track, edit, consentGranted)
            _state.value = _state.value.copy(saving = false)
            _events.emit(
                when (outcome) {
                    is SaveOutcome.Saved -> {
                        playback.refreshTrack(outcome.track)
                        EditorEvent.Saved
                    }
                    SaveOutcome.NeedsWritePermission -> EditorEvent.NeedsWritePermission
                    is SaveOutcome.NeedsConsent -> EditorEvent.NeedsConsent(outcome)
                    SaveOutcome.NeedsFolderAccess -> EditorEvent.NeedsFolderAccess
                    is SaveOutcome.Failed -> EditorEvent.Failed(outcome.reason)
                },
            )
        }
    }

    private suspend fun decode(bytes: ByteArray): Bitmap? = withContext(Dispatchers.Default) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= PREVIEW_PX && bounds.outHeight / (sample * 2) >= PREVIEW_PX) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    companion object {
        const val ARG_TRACK_ID = "trackId"
        private const val PREVIEW_PX = 300
    }
}
