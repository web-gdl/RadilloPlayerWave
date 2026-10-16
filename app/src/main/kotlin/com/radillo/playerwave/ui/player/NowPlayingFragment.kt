package com.radillo.playerwave.ui.player

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.radillo.playerwave.R
import com.radillo.playerwave.core.ui.ArtworkLoader
import com.radillo.playerwave.core.util.formatDuration
import com.radillo.playerwave.databinding.FragmentNowPlayingBinding
import com.radillo.playerwave.player.engine.PlaybackState
import com.radillo.playerwave.player.engine.RepeatMode
import com.radillo.playerwave.ui.ChromeMode
import com.radillo.playerwave.ui.common.collectWhenStarted
import com.radillo.playerwave.ui.common.onUserChange
import com.radillo.playerwave.ui.common.subtitle
import com.radillo.playerwave.ui.common.visibleIf
import com.radillo.playerwave.ui.effects.EqualizerFragment
import com.radillo.playerwave.ui.navigator
import dagger.hilt.android.AndroidEntryPoint

/** Pantalla «Reproduciendo»: carátula, progreso, controles, favorito, temporizador, ecualizador y cola. */
@AndroidEntryPoint
class NowPlayingFragment : Fragment(R.layout.fragment_now_playing), ChromeMode {

    override val hidesMiniPlayer: Boolean get() = true

    private val viewModel: PlayerViewModel by activityViewModels()
    private var binding: FragmentNowPlayingBinding? = null
    private var dragging = false
    private var lastArt: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentNowPlayingBinding.bind(view)
        binding = b
        b.btnPlay.setOnClickListener { viewModel.togglePlayPause() }
        b.btnNext.setOnClickListener { viewModel.next() }
        b.btnPrevious.setOnClickListener { viewModel.previous() }
        b.btnShuffle.setOnClickListener { viewModel.toggleShuffle() }
        b.btnRepeat.setOnClickListener { viewModel.cycleRepeat() }
        b.btnFavorite.setOnClickListener { viewModel.toggleFavorite() }
        b.btnSleep.setOnClickListener { showSleepDialog() }
        b.btnEqualizer.setOnClickListener { navigator.push(EqualizerFragment()) }
        b.btnQueue.setOnClickListener { navigator.push(QueueFragment()) }
        b.seek.onUserChange(
            onChange = { progress ->
                val duration = viewModel.state.value.durationMs
                b.position.text = formatDuration(progress * duration / 1000L)
            },
            onStart = { dragging = true },
            onStop = {
                dragging = false
                val duration = viewModel.state.value.durationMs
                viewModel.seekTo(b.seek.progress * duration / 1000L)
            },
        )

        collectWhenStarted(viewModel.state, ::render)
        collectWhenStarted(viewModel.isFavorite) { fav ->
            b.btnFavorite.setImageResource(if (fav) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
            b.btnFavorite.isActivated = fav
            b.btnFavorite.contentDescription = getString(if (fav) R.string.action_remove_favorite else R.string.action_add_favorite)
        }
        collectWhenStarted(viewModel.sleepRemainingMs) { remaining ->
            b.btnSleep.isActivated = remaining != null
            b.sleepRemaining.visibleIf(remaining != null)
            if (remaining != null) b.sleepRemaining.text = formatDuration(remaining)
        }
        collectWhenStarted(viewModel.accent) { accent ->
            val color = accent ?: requireContext().getColor(R.color.accent)
            b.btnPlay.backgroundTintList = ColorStateList.valueOf(color)
            b.seek.progressTintList = ColorStateList.valueOf(color)
            b.seek.thumbTintList = ColorStateList.valueOf(color)
        }
    }

    override fun onDestroyView() {
        binding = null
        lastArt = null
        super.onDestroyView()
    }

    private fun render(state: PlaybackState) {
        val b = binding ?: return
        val track = state.currentTrack
        b.contentGroup.visibleIf(track != null)
        b.emptyText.visibleIf(track == null)
        if (track == null) return
        b.trackTitle.text = track.title
        b.trackSubtitle.text = track.subtitle(requireContext())
        if (lastArt != track.albumArtUri) {
            lastArt = track.albumArtUri
            ArtworkLoader.get(requireContext()).load(b.art, track.albumArtUri, b.art.layoutParams.width, R.drawable.tile_note)
        }
        b.btnPlay.setImageResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        b.btnPlay.contentDescription = getString(if (state.isPlaying) R.string.action_pause else R.string.action_play)
        b.btnShuffle.isActivated = state.shuffleEnabled
        b.btnRepeat.isActivated = state.repeatMode != RepeatMode.OFF
        b.btnRepeat.setImageResource(if (state.repeatMode == RepeatMode.ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat)
        b.duration.text = formatDuration(state.durationMs)
        if (!dragging) {
            val duration = state.durationMs.coerceAtLeast(1L)
            b.seek.progress = (state.positionMs * 1000L / duration).toInt().coerceIn(0, 1000)
            b.position.text = formatDuration(state.positionMs)
        }
    }

    private fun showSleepDialog() {
        val minutes = intArrayOf(10, 15, 30, 45, 60, 90)
        val remaining = viewModel.sleepRemainingMs.value
        val labels = minutes.map { getString(R.string.sleep_timer_minutes, it) }.toMutableList()
        if (remaining != null) labels += getString(R.string.sleep_timer_cancel)
        val builder = AlertDialog.Builder(requireContext())
            .setTitle(
                if (remaining != null) getString(R.string.sleep_timer_remaining, formatDuration(remaining))
                else getString(R.string.sleep_timer),
            )
            .setItems(labels.toTypedArray()) { _, which ->
                if (which < minutes.size) viewModel.startSleepTimer(minutes[which]) else viewModel.cancelSleepTimer()
            }
            .setNegativeButton(R.string.action_cancel, null)
        builder.show()
    }
}
