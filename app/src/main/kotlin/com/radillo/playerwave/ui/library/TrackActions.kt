package com.radillo.playerwave.ui.library

import android.app.AlertDialog
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.radillo.playerwave.R
import com.radillo.playerwave.core.model.Track
import com.radillo.playerwave.ui.common.showTextInputDialog

/** Acción adicional que una pantalla añade al menú de una pista (p. ej. «Quitar de la lista»). */
class ExtraAction(@StringRes val label: Int, val run: () -> Unit)

/**
 * Menú de una pista como diálogo de lista de plataforma (sin hojas inferiores animadas):
 * reproducir a continuación, añadir a la cola, favorito, añadir a una lista y extras.
 */
fun Fragment.showTrackActions(
    track: Track,
    actions: TrackActionsViewModel,
    extras: List<ExtraAction> = emptyList(),
) {
    val context = requireContext()
    val isFavorite = track.id in actions.favoriteIds.value
    val labels = mutableListOf(
        context.getString(R.string.action_play_next),
        context.getString(R.string.action_add_to_queue),
        context.getString(if (isFavorite) R.string.action_remove_favorite else R.string.action_add_favorite),
        context.getString(R.string.action_add_to_playlist),
    )
    extras.forEach { labels += context.getString(it.label) }
    AlertDialog.Builder(context)
        .setTitle(track.title)
        .setItems(labels.toTypedArray()) { _, which ->
            when (which) {
                0 -> actions.playNext(track)
                1 -> actions.addToQueue(track)
                2 -> actions.toggleFavorite(track)
                3 -> showPlaylistPicker(track, actions)
                else -> extras.getOrNull(which - 4)?.run?.invoke()
            }
        }
        .show()
}

private fun Fragment.showPlaylistPicker(track: Track, actions: TrackActionsViewModel) {
    val context = requireContext()
    val playlists = actions.playlistList.value
    val createNew: () -> Unit = {
        context.showTextInputDialog(R.string.playlist_new, "", R.string.action_create, R.string.playlist_name_hint) { name ->
            actions.createPlaylistWith(name, track)
        }
    }
    if (playlists.isEmpty()) {
        createNew()
        return
    }
    val labels = playlists.map { it.name } + context.getString(R.string.playlist_new)
    AlertDialog.Builder(context)
        .setTitle(R.string.action_add_to_playlist)
        .setItems(labels.toTypedArray()) { _, which ->
            val playlist = playlists.getOrNull(which)
            if (playlist == null) createNew() else actions.addToPlaylist(playlist.id, track)
        }
        .setNegativeButton(R.string.action_cancel, null)
        .show()
}
