package com.radillo.playerwave.player.engine

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.radillo.playerwave.core.model.Track
import com.radillo.playerwave.visualizer.analysis.PlaybackClock
import com.radillo.playerwave.player.service.PlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de [PlaybackController] sobre un `MediaController` conectado a [PlaybackService].
 *
 * - Se conecta de forma perezosa la primera vez que se observa o se envía un comando.
 * - Los comandos recibidos antes de que termine la conexión se encolan y se ejecutan después.
 * - Todas las llamadas al controlador se hacen en el hilo principal, como exige Media3.
 */
@Singleton
class MediaControllerPlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlaybackController, PlaybackClock {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pending = ArrayDeque<(MediaController) -> Unit>()
    private var progressJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                )
            ) {
                publish(player)
            }
        }
    }

    init {
        connect()
    }

    private fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val c = try {
                    future.get()
                } catch (e: Exception) {
                    controllerFuture = null
                    return@addListener
                }
                controller = c
                c.addListener(listener)
                publish(c)
                while (pending.isNotEmpty()) pending.removeFirst().invoke(c)
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private fun run(command: (MediaController) -> Unit) {
        val c = controller
        if (c != null) command(c) else {
            pending.addLast(command)
            connect()
        }
    }

    private fun publish(player: Player) {
        val queue = readQueue(player)
        val index = player.currentMediaItemIndex
        val current = player.currentMediaItem?.let(MediaItemMapper::toTrack)
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: current?.durationMs ?: 0L
        _state.update {
            it.copy(
                currentTrack = current,
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = duration,
                shuffleEnabled = player.shuffleModeEnabled,
                repeatMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                    else -> RepeatMode.OFF
                },
                queue = queue,
                queueIndex = if (queue.isEmpty()) -1 else index,
            )
        }
        ensureProgressTicker(player.isPlaying)
    }

    private fun readQueue(player: Player): List<Track> {
        val timeline: Timeline = player.currentTimeline
        if (timeline.isEmpty) return emptyList()
        val window = Timeline.Window()
        val result = ArrayList<Track>(timeline.windowCount)
        for (i in 0 until timeline.windowCount) {
            timeline.getWindow(i, window)
            MediaItemMapper.toTrack(window.mediaItem)?.let(result::add)
        }
        return result
    }

    /** Mientras suena, refresca la posición dos veces por segundo para la barra de progreso. */
    private fun ensureProgressTicker(isPlaying: Boolean) {
        if (!isPlaying) {
            progressJob?.cancel()
            progressJob = null
            return
        }
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            while (isActive) {
                val c = controller ?: break
                _state.update { it.copy(positionMs = c.currentPosition.coerceAtLeast(0L)) }
                delay(500)
            }
        }
    }

    // ---- PlaybackClock (hilo principal, llamado en cada frame de render) ----

    override fun positionUs(): Long? {
        val c = controller ?: return null
        if (c.mediaItemCount == 0) return null
        return c.currentPosition * 1000L
    }

    override fun isPlaying(): Boolean = controller?.isPlaying == true

    // ---- Comandos ----

    override fun play() = run { it.play() }
    override fun pause() = run { it.pause() }
    override fun togglePlayPause() = run { if (it.isPlaying) it.pause() else it.play() }
    override fun next() = run { it.seekToNextMediaItem() }
    override fun previous() = run {
        // Como la mayoría de reproductores: si llevamos más de 3 s, vuelve al principio.
        if (it.currentPosition > 3_000 || !it.hasPreviousMediaItem()) it.seekTo(0) else it.seekToPreviousMediaItem()
    }

    override fun seekTo(positionMs: Long) = run { it.seekTo(positionMs) }
    override fun setShuffle(enabled: Boolean) = run { it.shuffleModeEnabled = enabled }
    override fun setRepeatMode(mode: RepeatMode) = run {
        it.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
    }

    override fun playQueue(tracks: List<Track>, startIndex: Int) = run {
        if (tracks.isEmpty()) return@run
        val items: List<MediaItem> = tracks.map(MediaItemMapper::toMediaItem)
        it.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), C.TIME_UNSET)
        it.prepare()
        it.play()
    }

    override fun addToQueue(tracks: List<Track>) = run {
        if (tracks.isEmpty()) return@run
        it.addMediaItems(tracks.map(MediaItemMapper::toMediaItem))
        if (it.playbackState == Player.STATE_IDLE) it.prepare()
    }

    override fun playNext(tracks: List<Track>) = run {
        if (tracks.isEmpty()) return@run
        val insertAt = if (it.mediaItemCount == 0) 0 else it.currentMediaItemIndex + 1
        it.addMediaItems(insertAt, tracks.map(MediaItemMapper::toMediaItem))
        if (it.playbackState == Player.STATE_IDLE) it.prepare()
    }

    override fun removeFromQueue(index: Int) = run {
        if (index in 0 until it.mediaItemCount) it.removeMediaItem(index)
    }

    override fun skipToQueueItem(index: Int) = run {
        if (index in 0 until it.mediaItemCount) {
            it.seekToDefaultPosition(index)
            it.play()
        }
    }
}
