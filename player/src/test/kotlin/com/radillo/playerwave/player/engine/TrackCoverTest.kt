package com.radillo.playerwave.player.engine

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.radillo.playerwave.core.ui.TrackCover
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** La portada de cada canción: URI propia, lectura de archivos y respaldo sin portada. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackCoverTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `cada cancion tiene su propia uri de portada con el archivo y el album`() {
        val a = Uri.parse(TrackCover.uri(7, "content://media/external/audio/media/7", 3, 1234))
        val b = Uri.parse(TrackCover.uri(8, "content://media/external/audio/media/8", 3, 1234))
        assertThat(a.scheme).isEqualTo(TrackCover.SCHEME)
        assertThat(a.getQueryParameter("file")).isEqualTo("content://media/external/audio/media/7")
        assertThat(a.getQueryParameter("album")).isEqualTo("3")
        // Mismo álbum, canciones distintas: ya no comparten portada.
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `lee archivos y no falla si la cancion no existe ni trae portada`() {
        val f = File(context.cacheDir, "cover.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertThat(TrackCover.readBytes(context, Uri.fromFile(f))).isEqualTo(byteArrayOf(1, 2, 3))
        val missing = Uri.parse(TrackCover.uri(9, "file:///no/existe.mp3", 99, 0))
        assertThat(TrackCover.readBytes(context, missing)).isNull()
    }
}
