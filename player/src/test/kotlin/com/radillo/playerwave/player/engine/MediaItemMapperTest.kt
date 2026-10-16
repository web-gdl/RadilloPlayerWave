package com.radillo.playerwave.player.engine

import androidx.media3.common.MediaItem
import com.google.common.truth.Truth.assertThat
import com.radillo.playerwave.core.model.Track
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaItemMapperTest {

    private val track = Track(
        id = 42L,
        uri = "content://media/external/audio/media/42",
        title = "Canción",
        artist = "Artista",
        artistId = 7L,
        album = "Álbum",
        albumId = 9L,
        durationMs = 215_000L,
        trackNumber = 3,
        year = 2021,
        folderPath = "/storage/emulated/0/Music",
        fileName = "cancion.mp3",
        mimeType = "audio/mpeg",
        dateAddedSec = 1_700_000_000L,
        sizeBytes = 5_000_000L,
    )

    @Test
    fun `ida y vuelta conserva todos los campos`() {
        val item = MediaItemMapper.toMediaItem(track)
        assertThat(item.mediaId).isEqualTo("42")
        assertThat(item.localConfiguration?.uri.toString()).isEqualTo(track.uri)
        assertThat(item.mediaMetadata.title.toString()).isEqualTo("Canción")
        assertThat(item.mediaMetadata.artworkUri.toString()).isEqualTo(track.albumArtUri)

        val back = MediaItemMapper.toTrack(item)
        assertThat(back).isEqualTo(track)
    }

    @Test
    fun `items ajenos devuelven null`() {
        val foreign = MediaItem.Builder().setMediaId("abc").setUri("content://x/1").build()
        assertThat(MediaItemMapper.toTrack(foreign)).isNull()
    }

    @Test
    fun `withPlayableUri restaura la uri desde los extras o el id`() {
        val original = MediaItemMapper.toMediaItem(track)
        // Simula un item recibido por el servicio sin localConfiguration.
        val stripped = MediaItem.Builder()
            .setMediaId(original.mediaId)
            .setMediaMetadata(original.mediaMetadata)
            .build()
        assertThat(stripped.localConfiguration).isNull()

        val restored = MediaItemMapper.withPlayableUri(stripped)
        assertThat(restored.localConfiguration?.uri.toString()).isEqualTo(track.uri)

        val onlyId = MediaItem.Builder().setMediaId("99").build()
        assertThat(MediaItemMapper.withPlayableUri(onlyId).localConfiguration?.uri.toString())
            .isEqualTo("content://media/external/audio/media/99")
    }
}
