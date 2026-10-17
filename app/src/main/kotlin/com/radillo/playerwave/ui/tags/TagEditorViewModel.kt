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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Estado de la pantalla. `cover` null = mostrar la carátula actual de la app (o el marcador). */
data class EditorState(
    val track: Track? = null,
    /** Motivo por el que no se puede guardar (no es MP3, etiqueta ilegible…), o null. */
    val problem: SaveOutcome.Reason? = null,
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
    /** Canal con búfer: un evento que llega con la app en segundo plano se entrega al volver. */
    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = _events.receiveAsFlow()

    /** Valores con que se abrió el formulario (sobreviven a girar la pantalla). */
    var initialValues: List<String>? = null
    private var filled = false
    /** El usuario negó el permiso de almacenamiento: se ofrece la carpeta en su lugar. */
    private var permissionDenied = false
    /** Ya se concedió una carpeta en este intento: si aun así no vale, no se vuelve a pedir. */
    private var folderGrantedThisTime = false

    private var coverChange: CoverChange = CoverChange.Keep
    /** Última edición pedida: se reintenta tras conceder un permiso. */
    private var pendingEdit: TagEdit? = null

    val hasCoverChange: Boolean get() = coverChange !is CoverChange.Keep

    init {
        viewModelScope.launch {
            val track = library.track(trackId) ?: playback.state.value.queue.firstOrNull { it.id == trackId }
            if (track == null) {
                _state.value = EditorState(loaded = true, problem = SaveOutcome.Reason.NOT_FOUND)
                return@launch
            }
            val info = writer.inspect(track)
            _state.value = EditorState(
                track = track,
                problem = info.problem,
                cover = info.embeddedCover?.let { decode(it) },
                loaded = true,
            )
        }
    }

    /** La canción para rellenar el formulario, solo la primera vez (luego manda lo escrito). */
    fun takeInitialFill(): Track? {
        if (filled) return null
        val track = _state.value.track ?: return null
        filled = true
        return track
    }

    fun pickCover(uri: Uri) {
        viewModelScope.launch {
            val bytes = writer.prepareCover(uri)
            if (bytes == null) {
                _events.send(EditorEvent.ImageFailed)
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
        folderGrantedThisTime = false
        run(edit, consentGranted = false)
    }

    /** Tras conceder permiso o consentimiento. */
    fun retry(consentGranted: Boolean) {
        pendingEdit?.let { run(it, consentGranted) }
    }

    fun permissionDenied() {
        permissionDenied = true
        retry(consentGranted = false)
    }

    fun folderGranted(treeUri: Uri) {
        writer.rememberFolder(treeUri)
        folderGrantedThisTime = true
        retry(consentGranted = false)
    }

    fun canPickFolders(): Boolean = writer.canPickFolders()

    private fun run(edit: TagEdit, consentGranted: Boolean) {
        val track = _state.value.track ?: return
        if (_state.value.saving) return
        _state.value = _state.value.copy(saving = true)
        viewModelScope.launch {
            // Si es la canción que suena, el reproductor suelta el archivo mientras se reescribe.
            if (playback.holdForEdit(track.id)) delay(HOLD_SETTLE_MS)
            val outcome = writer.save(track, edit, consentGranted, mayAskPermission = !permissionDenied)
            playback.refreshTrack((outcome as? SaveOutcome.Saved)?.track)
            _state.value = _state.value.copy(saving = false)
            _events.send(
                when (outcome) {
                    is SaveOutcome.Saved -> EditorEvent.Saved
                    SaveOutcome.NeedsWritePermission -> EditorEvent.NeedsWritePermission
                    is SaveOutcome.NeedsConsent -> EditorEvent.NeedsConsent(outcome)
                    // Ya se eligió una carpeta y no sirve (otra memoria): no se entra en bucle.
                    SaveOutcome.NeedsFolderAccess ->
                        if (folderGrantedThisTime) EditorEvent.Failed(SaveOutcome.Reason.NO_ACCESS) else EditorEvent.NeedsFolderAccess
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
        /** Margen para que el reproductor suelte el archivo antes de reescribirlo. */
        private const val HOLD_SETTLE_MS = 400L
    }
}
