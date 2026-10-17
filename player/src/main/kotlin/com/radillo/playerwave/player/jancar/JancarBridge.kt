package com.radillo.playerwave.player.jancar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.radillo.playerwave.core.diagnostics.EventLog
import java.nio.ByteBuffer

/**
 * Puente con el estéreo Jancar: registra la app como fuente de audio del sistema, igual que la
 * música de fábrica. Con eso:
 *
 * - las teclas del volante y del panel llegan directamente a la app;
 * - las llamadas, el asistente de voz o la pantalla apagada la pausan y la reanudan;
 * - al apagar el auto no se cierra a la fuerza (el sistema le pide salir) y al encender sigue;
 * - al arrancar el estéreo el sistema la vuelve a abrir ("app de memoria");
 * - el sistema sabe qué canción suena.
 *
 * **Mientras está conectado, el foco de audio lo pide el servicio en nombre de la app**: la app no
 * debe pedirlo (si lo hiciera, el servicio entendería que otra app tomó el audio y la detendría).
 * [onActiveChanged] avisa al servicio de reproducción para que cambie de modo.
 *
 * Si el equipo no tiene el servicio, [create] devuelve `null` y la app funciona como siempre.
 * Todo ocurre en el hilo principal salvo las llamadas binder (hilo propio del cliente).
 */
internal class JancarBridge private constructor(
    private val context: Context,
    private val player: ExoPlayer,
    private val onActiveChanged: () -> Unit,
) : Player.Listener, JancarMediaClient.Listener {

    private val client = JancarMediaClient(context, this)
    private val handler = SourceCommandHandler(PlayerAdapter())
    private val main = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private var connected = false
    private var registered = false
    private var failed = false
    private var released = false
    private var lastInfoKey: String? = null
    private var lastState: Triple<Int, Int, Int>? = null

    /** Con el servicio conectado y sano, el foco de audio es cosa suya. */
    val active: Boolean get() = connected && !failed && !released

    /**
     * Lo que el usuario quería la última vez: sonando o en pausa. Las pausas del sistema (apagar,
     * llamadas) no lo cambian. Decide si se reanuda cuando el estéreo abre la app al arrancar.
     */
    val userWantsPlayback: Boolean get() = prefs.getBoolean(KEY_WANTS_PLAYBACK, false)

    private val ticker = object : Runnable {
        override fun run() {
            publishState()
            if (registered && player.isPlaying) main.postDelayed(this, STATE_INTERVAL_MS)
        }
    }

    private fun start() {
        JancarStatus.link = JancarStatus.Link.CONNECTING
        player.addListener(this)
        if (!client.bind()) {
            JancarStatus.link = JancarStatus.Link.LOST
            EventLog.log("Estéreo: el servicio de medios existe pero no se pudo conectar")
        }
    }

    fun release() {
        if (released) return
        player.removeListener(this)
        main.removeCallbacks(ticker)
        val close = if (registered) JancarProtocol.TYPE_THIRD_PARTY else null
        released = true
        client.release(close)
        registered = false
        connected = false
        JancarStatus.registered = false
        JancarStatus.link = JancarStatus.Link.LOST
    }

    // ---- Conexión ----

    override fun onConnected() {
        if (released) return
        val first = !connected
        connected = true
        failed = false
        JancarStatus.link = JancarStatus.Link.CONNECTED
        if (first) EventLog.log("Estéreo: conectado al servicio de medios")
        onActiveChanged()
        if (player.playWhenReady) register()
    }

    override fun onDisconnected() {
        if (released || !connected) return
        connected = false
        registered = false
        main.removeCallbacks(ticker)
        JancarStatus.link = JancarStatus.Link.LOST
        JancarStatus.registered = false
        EventLog.log("Estéreo: se perdió el servicio de medios; la app maneja el audio sola")
        // Si se fue en plena atenuación, nadie devolvería el volumen.
        player.volume = 1f
        onActiveChanged()
    }

    override fun onCallFailed(what: String) {
        JancarStatus.failedCalls++
        if (!connected || failed) return
        // Si el servicio no responde, la app vuelve a pedir el foco por su cuenta para no quedarse
        // sonando encima de la radio. Se reintenta al próximo «reproducir».
        failed = true
        registered = false
        JancarStatus.registered = false
        main.removeCallbacks(ticker)
        player.volume = 1f
        onActiveChanged()
    }

    override fun onCommand(command: JancarCommand) {
        if (released) return
        JancarStatus.count(command.label)
        when (command) {
            JancarCommand.Suspend, JancarCommand.Resume, JancarCommand.Stop, is JancarCommand.Quit ->
                EventLog.log("Estéreo pidió: ${command.label}" + if (command is JancarCommand.Quit) " (origen ${command.source})" else "")
            else -> Unit
        }
        when (handler.handle(command)) {
            SourceCommandHandler.Outcome.KEEP -> Unit
            SourceCommandHandler.Outcome.RELEASED -> {
                // Pudo ser otra fuente que tomó el audio: al volver a sonar hay que registrarse.
                registered = false
                JancarStatus.registered = false
                main.removeCallbacks(ticker)
            }
            SourceCommandHandler.Outcome.CLOSE -> {
                setUserWantsPlayback(false)
                if (registered) client.close(JancarProtocol.TYPE_THIRD_PARTY)
                registered = false
                JancarStatus.registered = false
                main.removeCallbacks(ticker)
            }
        }
    }

    // ---- Reproductor ----

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (handler.onPlayWhenReadyChanged(playWhenReady)) setUserWantsPlayback(playWhenReady)
        if (playWhenReady) {
            if (failed && connected) {
                // Nuevo intento tras un fallo: si el servicio responde, vuelve a mandar él.
                failed = false
                onActiveChanged()
            }
            register()
        }
        publishState()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        main.removeCallbacks(ticker)
        if (isPlaying && registered) main.postDelayed(ticker, STATE_INTERVAL_MS)
        publishState()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        publishInfo()
        publishState()
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = publishInfo()

    override fun onPlaybackStateChanged(playbackState: Int) = publishState()

    private fun register() {
        if (!active || registered) return
        registered = true
        JancarStatus.registered = true
        lastInfoKey = null
        lastState = null
        client.open(JancarProtocol.TYPE_THIRD_PARTY)
        EventLog.log("Estéreo: registrada como fuente de audio")
        publishInfo()
        publishState()
        if (player.isPlaying) {
            main.removeCallbacks(ticker)
            main.postDelayed(ticker, STATE_INTERVAL_MS)
        }
    }

    private fun publishState() {
        if (!registered) return
        val hasItems = player.mediaItemCount > 0
        val state = JancarProtocol.stateFor(
            playWhenReady = player.playWhenReady,
            ended = player.playbackState == Player.STATE_ENDED,
            hasItems = hasItems,
        )
        val position = (player.currentPosition.coerceAtLeast(0L) / 1000L).toInt()
        val duration = player.duration.takeIf { it > 0L }?.let { (it / 1000L).toInt() } ?: 0
        val now = Triple(state, position, duration)
        if (now == lastState) return
        lastState = now
        client.setMediaState(JancarProtocol.TYPE_THIRD_PARTY, state, position, duration)
    }

    private fun publishInfo() {
        if (!registered) return
        val item = player.currentMediaItem ?: return
        val meta = item.mediaMetadata
        val title = meta.title?.toString().orEmpty()
        val artist = meta.artist?.toString().orEmpty()
        val art = meta.artworkUri
        val index = player.currentMediaItemIndex + 1
        val total = player.mediaItemCount
        val key = "$title|$artist|$art|$index|$total"
        if (key == lastInfoKey) return
        lastInfoKey = key
        // La carátula se decodifica en el hilo del puente, justo antes de enviarla y en orden.
        client.runInBackground {
            val pixels = art?.let(::loadArt)
            client.setMediaInfo(
                JancarProtocol.TYPE_THIRD_PARTY,
                title,
                artist,
                pixels?.width ?: 0,
                pixels?.height ?: 0,
                pixels?.bytes ?: ByteArray(0),
                index,
                total,
            )
        }
    }

    private class Art(val width: Int, val height: Int, val bytes: ByteArray)

    /** Carátula reducida a ≤240 px en RGB_565 (la mitad de bytes que ARGB; el servicio la acepta). */
    private fun loadArt(uri: Uri): Art? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openArt(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            val options = BitmapFactory.Options().apply {
                inSampleSize = ArtSize.sampleSize(bounds.outWidth, bounds.outHeight, JancarProtocol.ART_MAX_PX)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val decoded = openArt(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            decoded?.let { bmp ->
                val (w, h) = ArtSize.fitWithin(bmp.width, bmp.height, JancarProtocol.ART_MAX_PX)
                val scaled = if (w == bmp.width && h == bmp.height) bmp else Bitmap.createScaledBitmap(bmp, w, h, true)
                val rgb = if (scaled.config == Bitmap.Config.RGB_565) scaled else scaled.copy(Bitmap.Config.RGB_565, false)
                val buffer = ByteBuffer.allocate(rgb.rowBytes * rgb.height)
                rgb.copyPixelsToBuffer(buffer)
                val result = Art(rgb.width, rgb.height, buffer.array())
                if (rgb !== bmp) rgb.recycle()
                if (scaled !== bmp && scaled !== rgb) scaled.recycle()
                bmp.recycle()
                // El servicio deduce el formato por el tamaño: debe ser exactamente ancho×alto×2.
                result.takeIf { it.bytes.size == it.width * it.height * 2 }
            }
        }
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }

    private fun openArt(uri: Uri) = when (uri.scheme) {
        "file" -> uri.path?.let { java.io.File(it).inputStream() }
        else -> context.contentResolver.openInputStream(uri)
    }

    private fun setUserWantsPlayback(value: Boolean) {
        if (prefs.getBoolean(KEY_WANTS_PLAYBACK, false) != value) {
            prefs.edit().putBoolean(KEY_WANTS_PLAYBACK, value).apply()
        }
    }

    /** El reproductor visto por el manejador de órdenes. */
    private inner class PlayerAdapter : SourcePlayer {
        override val playWhenReady: Boolean get() = player.playWhenReady
        override fun play() {
            if (player.mediaItemCount == 0) return
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
            player.play()
        }
        override fun pause() = player.pause()
        override fun next() = player.seekToNextMediaItem()
        override fun previous() = player.seekToPrevious()
        override fun seekTo(positionMs: Long) = player.seekTo(positionMs)
        override fun setVolume(volume: Float) {
            player.volume = volume
        }
    }

    companion object {
        private const val PREFS = "jancar_bridge"
        private const val KEY_WANTS_PLAYBACK = "user_wants_playback"
        private const val STATE_INTERVAL_MS = 1_000L

        /** Crea el puente solo si el estéreo tiene el servicio de medios de Jancar. */
        fun create(context: Context, player: ExoPlayer, onActiveChanged: () -> Unit): JancarBridge? {
            if (!JancarMediaClient.isServiceInstalled(context)) {
                JancarStatus.link = JancarStatus.Link.ABSENT
                return null
            }
            return JancarBridge(context.applicationContext, player, onActiveChanged).also { it.start() }
        }
    }
}
