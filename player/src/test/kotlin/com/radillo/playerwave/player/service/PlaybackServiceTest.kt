package com.radillo.playerwave.player.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Prueba de ciclo de vida del servicio: se crea, expone una sesión y se libera sin excepciones.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackServiceTest {

    @Test
    fun `el servicio crea una sesion y la libera al destruirse`() {
        val controller = Robolectric.buildService(PlaybackService::class.java)
        val service = controller.create().get()

        val session = service.sessionForTest()
        assertThat(session).isNotNull()
        assertThat(session!!.player.mediaItemCount).isEqualTo(0)
        assertThat(session.player.isPlaying).isFalse()

        controller.destroy()
        assertThat(service.sessionForTest()).isNull()
    }
}
