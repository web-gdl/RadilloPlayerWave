package com.radillo.playerwave.ui.tags

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.radillo.playerwave.R
import com.radillo.playerwave.core.ui.ArtworkLoader
import com.radillo.playerwave.data.tags.SaveOutcome
import com.radillo.playerwave.databinding.FragmentTagEditorBinding
import com.radillo.playerwave.ui.ChromeMode
import com.radillo.playerwave.ui.common.collectWhenStarted
import com.radillo.playerwave.ui.common.visibleIf
import com.radillo.playerwave.ui.navigator
import dagger.hilt.android.AndroidEntryPoint

/**
 * Editor de etiquetas y portada de una canción. Escribe dentro del MP3 ([com.radillo.playerwave.data.tags.TagWriter]);
 * si el sistema pide algo (permiso, consentimiento o la carpeta de la USB) lo solicita aquí y
 * reintenta solo. Al salir con cambios sin guardar pregunta antes de descartarlos.
 */
@AndroidEntryPoint
class TagEditorFragment : Fragment(R.layout.fragment_tag_editor), ChromeMode {

    override val hidesMiniPlayer: Boolean get() = true

    private val viewModel: TagEditorViewModel by viewModels()
    private var binding: FragmentTagEditorBinding? = null
    private var initialValues: List<String>? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.pickCover(uri)
    }
    private val writePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.retry(consentGranted = false) else toast(R.string.tag_error_permission)
    }
    private val consent = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.retry(consentGranted = true)
    }
    private val folder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.folderGranted(uri)
    }

    private val backGuard = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = confirmLeave()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentTagEditorBinding.bind(view)
        binding = b
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backGuard)
        b.btnBack.setOnClickListener { confirmLeave() }
        b.btnSave.setOnClickListener { save() }
        b.btnChangeCover.setOnClickListener { chooseImage() }
        b.cover.setOnClickListener { chooseImage() }
        b.btnRemoveCover.setOnClickListener { viewModel.removeCover() }

        collectWhenStarted(viewModel.state, ::render)
        collectWhenStarted(viewModel.events, ::onEvent)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun render(s: EditorState) {
        val b = binding ?: return
        val track = s.track
        if (track != null && initialValues == null) {
            // Solo la primera vez: después manda lo que escribe el usuario.
            b.fieldTitle.setText(track.title)
            b.fieldArtist.setText(track.artist)
            b.fieldAlbum.setText(track.album)
            b.fieldYear.setText(track.year.takeIf { it > 0 }?.toString().orEmpty())
            b.fieldTrack.setText(track.trackNumber.takeIf { it > 0 }?.toString().orEmpty())
            initialValues = currentValues(b)
        }
        when {
            s.cover != null -> b.cover.setImageBitmap(s.cover)
            s.coverRemoved || track == null -> b.cover.setImageResource(R.drawable.tile_note)
            else -> ArtworkLoader.get(requireContext()).load(b.cover, track.albumArtUri, b.cover.layoutParams.width.coerceAtLeast(240), R.drawable.tile_note)
        }
        val editable = s.loaded && s.supported && track != null
        listOf<View>(b.fieldTitle, b.fieldArtist, b.fieldAlbum, b.fieldYear, b.fieldTrack, b.btnChangeCover, b.btnRemoveCover, b.cover)
            .forEach { it.isEnabled = editable && !s.saving }
        b.btnSave.isEnabled = editable && !s.saving
        b.btnSave.alpha = if (b.btnSave.isEnabled) 1f else 0.5f
        b.btnSave.setText(if (s.saving) R.string.tag_saving else R.string.action_save)
        b.saving.visibility = if (s.saving) View.VISIBLE else View.INVISIBLE
        b.notSupported.visibleIf(s.loaded && !s.supported)
        if (s.loaded && !s.supported) {
            val kind = track?.fileName?.substringAfterLast('.', "")?.uppercase()?.ifEmpty { null } ?: track?.mimeType.orEmpty()
            b.notSupported.text = getString(R.string.tag_not_mp3, kind)
        }
    }

    private fun currentValues(b: FragmentTagEditorBinding) =
        listOf(b.fieldTitle, b.fieldArtist, b.fieldAlbum, b.fieldYear, b.fieldTrack).map { it.text.toString() }

    private fun hasChanges(): Boolean {
        val b = binding ?: return false
        return viewModel.hasCoverChange || (initialValues != null && currentValues(b) != initialValues)
    }

    private fun save() {
        val b = binding ?: return
        val title = b.fieldTitle.text.toString().trim()
        if (title.isEmpty()) return fieldError(b.fieldTitle, R.string.tag_error_title_required)
        val yearText = b.fieldYear.text.toString().trim()
        val year = yearText.toIntOrNull()
        if (yearText.isNotEmpty() && (year == null || year !in 1000..2100)) return fieldError(b.fieldYear, R.string.tag_error_year)
        val trackText = b.fieldTrack.text.toString().trim()
        val trackNumber = trackText.toIntOrNull()
        if (trackText.isNotEmpty() && (trackNumber == null || trackNumber !in 1..999)) return fieldError(b.fieldTrack, R.string.tag_error_track)
        hideKeyboard()
        viewModel.save(title, b.fieldArtist.text.toString(), b.fieldAlbum.text.toString(), year, trackNumber)
    }

    private fun fieldError(field: EditText, message: Int) {
        field.error = getString(message)
        field.requestFocus()
    }

    private fun onEvent(event: EditorEvent) {
        when (event) {
            EditorEvent.Saved -> {
                toast(R.string.tag_saved)
                backGuard.isEnabled = false
                navigator.back()
            }
            EditorEvent.NeedsWritePermission -> writePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            is EditorEvent.NeedsConsent -> consent.launch(IntentSenderRequest.Builder(event.outcome.intentSender).build())
            EditorEvent.NeedsFolderAccess -> askFolder()
            is EditorEvent.Failed -> showError(event.reason)
            EditorEvent.ImageFailed -> toast(R.string.tag_error_image)
        }
    }

    /** Explica antes de abrir el selector de carpetas: sin contexto, el selector desconcierta. */
    private fun askFolder() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.tag_folder_access_title)
            .setMessage(R.string.tag_folder_access_body)
            .setPositiveButton(R.string.tag_folder_access_choose) { _, _ ->
                try {
                    folder.launch(null)
                } catch (e: ActivityNotFoundException) {
                    showError(SaveOutcome.Reason.NO_ACCESS)
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showError(reason: SaveOutcome.Reason) {
        val message = when (reason) {
            SaveOutcome.Reason.NOT_MP3 -> getString(R.string.tag_not_mp3, "")
            SaveOutcome.Reason.NOT_FOUND -> getString(R.string.tag_error_not_found)
            SaveOutcome.Reason.NO_SPACE -> getString(R.string.tag_error_space)
            SaveOutcome.Reason.INVALID_TAG -> getString(R.string.tag_error_invalid)
            SaveOutcome.Reason.NO_ACCESS -> getString(R.string.tag_error_no_access)
            SaveOutcome.Reason.IO -> getString(R.string.tag_error_io)
        }
        AlertDialog.Builder(requireContext())
            .setMessage(message)
            .setPositiveButton(R.string.action_close, null)
            .show()
    }

    private fun chooseImage() {
        try {
            pickImage.launch("image/*")
        } catch (e: ActivityNotFoundException) {
            toast(R.string.tag_error_no_picker)
        }
    }

    private fun confirmLeave() {
        if (!hasChanges()) {
            backGuard.isEnabled = false
            navigator.back()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.tag_discard_title)
            .setPositiveButton(R.string.tag_discard) { _, _ ->
                backGuard.isEnabled = false
                navigator.back()
            }
            .setNegativeButton(R.string.tag_keep_editing, null)
            .show()
    }

    private fun hideKeyboard() {
        val view = view ?: return
        (requireContext().getSystemService(InputMethodManager::class.java))?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun toast(message: Int) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        fun newInstance(trackId: Long) = TagEditorFragment().apply {
            arguments = bundleOf(TagEditorViewModel.ARG_TRACK_ID to trackId)
        }
    }
}
