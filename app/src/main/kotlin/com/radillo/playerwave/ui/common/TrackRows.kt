package com.radillo.playerwave.ui.common

import android.content.Context
import com.radillo.playerwave.R
import com.radillo.playerwave.core.model.Track

/** Subtítulo «artista · álbum» con textos de respaldo traducidos. */
fun Track.subtitle(context: Context): String =
    listOf(artist, album).filter { it.isNotBlank() }.joinToString(" · ")
        .ifBlank { context.getString(R.string.unknown_artist) }

fun Track.toRow(context: Context, nowPlayingId: Long?, key: String = "t$id"): Row = Row(
    key = key,
    title = title,
    subtitle = subtitle(context),
    artUri = albumArtUri,
    placeholder = R.drawable.tile_note,
    highlighted = id == nowPlayingId,
)

fun List<Track>.toRows(context: Context, nowPlayingId: Long?): List<Row> = map { it.toRow(context, nowPlayingId) }
