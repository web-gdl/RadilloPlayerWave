package com.radillo.playerwave.player.service

import android.media.AudioManager
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.radillo.playerwave.core.model.Track
import com.radillo.playerwave.player.engine.MediaItemMapper
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Ciclo de vida del servicio, persistencia de la cola y foco de audio de coche.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackServiceTest {

    private fun track(id: Long) = Track(
        id = id, uri = "content://media/external/audio/media/$id", title = "Canción $id", artist = "Artista",
        artistId = 1, album = "Álbum", albumId = 2, durationMs = 200_000, trackNumber = id.toInt(), year = 1999,
        folderPath = "/storage/usb/Música", fileName = "$id.mp3", mimeType = "audio/mpeg", dateAddedSec = 0, sizeBytes = 1,
    )

    @Test
    fun `el servicio crea una sesion y la libera al destruirse`() {
        val controller = Robolectric.buildService(PlaybackService::class.java)
        val service = controller.create().get()

        val session = service.sessionForTest()
        assertThat(session).isNotNull()
        assertThat(session!!.player.isPlaying).isFalse()

        controller.destroy()
        assertThat(service.sessionForTest()).isNull()
    }

    @Test
    fun `la cola, la posicion, el aleatorio y la repeticion sobreviven a reiniciar el servicio`() {
        val first = Robolectric.buildService(PlaybackService::class.java)
        val player = first.create().get().sessionForTest()!!.player
        player.setMediaItems(listOf(track(1), track(2), track(3)).map(MediaItemMapper::toMediaItem), 1, 42_000)
        player.shuffleModeEnabled = true
        player.repeatMode = Player.REPEAT_MODE_ALL
        first.destroy()

        val second = Robolectric.buildService(PlaybackService::class.java)
        val restored = second.create().get().sessionForTest()!!.player
        assertThat(restored.mediaItemCount).isEqualTo(3)
        assertThat(restored.currentMediaItemIndex).isEqualTo(1)
        assertThat(restored.currentPosition).isEqualTo(42_000)
        assertThat(restored.shuffleModeEnabled).isTrue()
        assertThat(restored.repeatMode).isEqualTo(Player.REPEAT_MODE_ALL)
        assertThat(MediaItemMapper.toTrack(restored.getMediaItemAt(2))?.title).isEqualTo("Canción 3")
        second.destroy()
    }

    @Test
    fun `el archivo de estado tolera datos daniados`() {
        val dir = ApplicationProvider.getApplicationContext<android.content.Context>().filesDir
        val file = File(dir, "estado_roto.json").apply { writeText("{ esto no es json") }
        assertThat(PlaybackStateStore(file).read()).isNull()
    }

    @Test
    fun `una cola enorme se recorta alrededor de la cancion actual`() {
        val tracks = (1L..6_000L).map(::track)
        val saved = SavedPlayback(tracks, index = 5_900, positionMs = 0, shuffle = false, repeatMode = 0)
        val trimmed = PlaybackStateStore.trimmed(saved)
        assertThat(trimmed.tracks).hasSize(PlaybackStateStore.MAX_TRACKS)
        assertThat(trimmed.tracks[trimmed.index].id).isEqualTo(5_901L)
        val decoded = PlaybackStateStore.decode(PlaybackStateStore.encode(saved))!!
        assertThat(decoded.tracks[decoded.index].id).isEqualTo(5_901L)
    }

    @Test
    fun `con el ajuste de coche una perdida permanente del audio no pausa, una breve si`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val player = ExoPlayer.Builder(context).build()
        val focus = KeepPlayingAudioFocus(context, player)
        focus.enabled = true
        player.play()
        focus.onPlayWhenReadyChanged(true)

        focus.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        assertThat(player.playWhenReady).isTrue()

        focus.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertThat(player.playWhenReady).isFalse()
        focus.onPlayWhenReadyChanged(false)
        focus.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertThat(player.playWhenReady).isTrue()

        focus.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        assertThat(player.volume).isLessThan(1f)
        focus.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertThat(player.volume).isEqualTo(1f)
        focus.release()
        player.release()
    }
}
