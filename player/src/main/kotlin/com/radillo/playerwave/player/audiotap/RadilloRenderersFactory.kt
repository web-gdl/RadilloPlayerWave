package com.radillo.playerwave.player.audiotap

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.video.VideoRendererEventListener

/**
 * Fábrica de renderers **solo audio** que inserta el tap de PCM en la cadena del `DefaultAudioSink`.
 *
 * - No crea renderers de vídeo, subtítulos, metadatos en flujo, movimiento de cámara ni imagen:
 *   menos clases cargadas y menos memoria en dispositivos de 1 GB (la app no reproduce vídeo).
 * - El offload de audio no se habilita: con offload el PCM no pasa por los `AudioProcessor`.
 * - `setEnableDecoderFallback(true)`: si el decodificador por hardware de la unidad falla con un
 *   archivo, se prueba el siguiente disponible en vez de detener la reproducción.
 */
@OptIn(UnstableApi::class)
class RadilloRenderersFactory(
    context: Context,
    private val tap: AudioProcessor,
) : DefaultRenderersFactory(context) {

    init {
        setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        setEnableDecoderFallback(true)
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink? {
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf(tap))
            .build()
    }

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>,
    ) = Unit

    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) = Unit

    override fun buildMetadataRenderers(
        context: Context,
        output: MetadataOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) = Unit

    override fun buildCameraMotionRenderers(
        context: Context,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) = Unit

    override fun buildImageRenderers(context: Context, out: ArrayList<Renderer>) = Unit
}
