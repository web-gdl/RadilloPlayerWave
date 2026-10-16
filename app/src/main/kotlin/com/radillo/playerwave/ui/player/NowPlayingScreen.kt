package com.radillo.playerwave.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radillo.playerwave.R
import com.radillo.playerwave.core.ui.components.ArtworkImage
import com.radillo.playerwave.core.util.formatDuration
import com.radillo.playerwave.player.engine.RepeatMode
import com.radillo.playerwave.ui.library.EmptyState

@Composable
fun NowPlayingScreen(
    onOpenQueue: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val track = state.currentTrack
    if (track == null) {
        EmptyState(stringResource(R.string.now_playing_empty))
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Spacer(Modifier.height(8.dp))
        ArtworkImage(
            model = track.albumArtUri,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .aspectRatio(1f),
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                track.title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · ")
                    .ifBlank { stringResource(R.string.unknown_artist) },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        SeekBar(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            onSeek = viewModel::seekTo,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToggleIcon(
                active = state.shuffleEnabled,
                onClick = viewModel::toggleShuffle,
                description = stringResource(R.string.action_shuffle),
            ) { Icon(Icons.Rounded.Shuffle, null) }

            IconButton(onClick = viewModel::previous, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.action_previous), Modifier.size(36.dp))
            }
            FilledIconButton(
                onClick = viewModel::togglePlayPause,
                modifier = Modifier.size(76.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (state.isPlaying) R.string.action_pause else R.string.action_play),
                    modifier = Modifier.size(40.dp),
                )
            }
            IconButton(onClick = viewModel::next, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipNext, stringResource(R.string.action_next), Modifier.size(36.dp))
            }

            ToggleIcon(
                active = state.repeatMode != RepeatMode.OFF,
                onClick = viewModel::cycleRepeat,
                description = stringResource(R.string.action_repeat),
            ) {
                Icon(if (state.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, null)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onOpenQueue) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.queue_title))
            }
        }
    }
}

@Composable
private fun ToggleIcon(
    active: Boolean,
    onClick: () -> Unit,
    description: String,
    icon: @Composable () -> Unit,
) {
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(contentColor = tint),
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            icon()
            if (active) {
                Canvas(Modifier.padding(top = 26.dp).size(4.dp)) { drawCircle(color = tint) }
            }
        }
    }
}

/**
 * Barra de progreso. Mientras el usuario arrastra, muestra la posición del dedo y solo
 * envía el seek al soltar, para no inundar al reproductor de comandos.
 */
@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val safeDuration = durationMs.coerceAtLeast(1L)
    val shown = if (dragging) dragValue else positionMs.toFloat() / safeDuration

    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = shown.coerceIn(0f, 1f),
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = {
                dragging = false
                onSeek((dragValue * safeDuration).toLong())
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatDuration(if (dragging) (dragValue * safeDuration).toLong() else positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatDuration(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
