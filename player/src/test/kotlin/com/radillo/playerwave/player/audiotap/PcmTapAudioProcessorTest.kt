package com.radillo.playerwave.player.audiotap

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.google.common.truth.Truth.assertThat
import com.radillo.playerwave.visualizer.analysis.AudioSourceStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PcmTapAudioProcessorTest {

    @Test
    fun `deja pasar el audio intacto y copia las muestras al anillo`() = runBlocking {
        val source = TapAudioSource()
        val processor = PcmTapAudioProcessor(source)

        val format = AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)
        val out = processor.configure(format)
        assertThat(processor.isActive).isTrue()
        assertThat(out.sampleRate).isEqualTo(48_000)
        assertThat(source.status.value).isEqualTo(AudioSourceStatus.ACTIVE)

        // 4 frames estéreo de 16 bits: L = 16384 (0.5), R = -16384 (-0.5).
        val frames = 4
        val input = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) {
            input.putShort(16_384)
            input.putShort(-16_384)
        }
        input.flip()
        val expected = ByteArray(frames * 4).also { input.duplicate().get(it) }

        processor.queueInput(input)
        val output = processor.output
        val actual = ByteArray(output.remaining()).also { output.get(it) }
        assertThat(actual).isEqualTo(expected)

        val block = source.blocks.first()
        assertThat(block.channelCount).isEqualTo(2)
        assertThat(block.sampleRateHz).isEqualTo(48_000)
        assertThat(block.samples).hasLength(frames * 2)
        assertThat(block.samples[0]).isWithin(1e-6f).of(0.5f)
        assertThat(block.samples[1]).isWithin(1e-6f).of(-0.5f)
        assertThat(source.writtenFrames).isEqualTo(frames.toLong())
    }

    @Test
    fun `un formato no soportado desactiva el procesador y marca la fuente como no disponible`() {
        val source = TapAudioSource()
        val processor = PcmTapAudioProcessor(source)
        val out = processor.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_24BIT))
        assertThat(processor.isActive).isFalse()
        assertThat(out).isEqualTo(AudioProcessor.AudioFormat.NOT_SET)
        assertThat(source.status.value).isEqualTo(AudioSourceStatus.UNAVAILABLE)
    }
}
