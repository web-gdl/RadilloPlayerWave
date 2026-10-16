package com.radillo.playerwave.player.audiotap

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * Fábrica de renderers que inserta el tap de PCM en la cadena del `DefaultAudioSink`.
 * El offload de audio no se habilita: con offload el PCM no pasa por los `AudioProcessor`.
 */
@OptIn(UnstableApi::class)
class RadilloRenderersFactory(
    context: Context,
    private val tap: AudioProcessor,
) : DefaultRenderersFactory(context) {

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
}
