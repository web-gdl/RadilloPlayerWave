package com.radillo.playerwave.player.jancar

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.radillo.playerwave.core.model.Track
import com.radillo.playerwave.player.engine.MediaItemMapper
import com.radillo.playerwave.player.service.PlaybackService
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.CopyOnWriteArrayList

/**
 * El puente con un servicio de estéreo falso que habla el mismo protocolo binder: registro como
 * fuente, órdenes del volante y del sistema, datos publicados, foco de audio y cierre.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JancarBridgeTest {

    /** Servicio de medios falso: lee las transacciones como lo haría el del estéreo. */
    private class FakeJancarService : Binder() {
        data class Call(val code: Int, val type: Int, val callback: IBinder? = null, val text: List<String?> = emptyList(), val ints: List<Int> = emptyList())

        val calls = CopyOnWriteArrayList<Call>()

        fun last(code: Int): Call? = calls.lastOrNull { it.code == code }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code > LAST_CALL_TRANSACTION) return super.onTransact(code, data, reply, flags)
            data.enforceInterface(JancarProtocol.MEDIA_DESCRIPTOR)
            val call = when (code) {
                JancarProtocol.TX_OPEN -> {
                    val type = data.readInt()
                    val cb = data.readStrongBinder()
                    Call(code, type, cb, listOf(data.readString()))
                }
                JancarProtocol.TX_CLOSE -> Call(code, data.readInt())
                JancarProtocol.TX_SET_MEDIA_STATE -> {
                    val type = data.readInt()
                    Call(code, type, ints = listOf(data.readInt(), data.readInt(), data.readInt()))
                }
                JancarProtocol.TX_SET_MEDIA_INFO -> {
                    val type = data.readInt()
                    val title = data.readString()
                    val artist = data.readString()
                    val w = data.readInt()
                    val h = data.readInt()
                    val pixels = data.createByteArray()
                    Call(code, type, text = listOf(title, artist), ints = listOf(w, h, pixels?.size ?: -1, data.readInt(), data.readInt(), data.readInt()))
                }
                else -> Call(code, -1)
            }
            calls += call
            reply?.writeNoException()
            return true
        }
    }

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val fake = FakeJancarService()

    private fun track(id: Long) = Track(
        id = id, uri = "content://media/external/audio/media/$id", title = "Canción $id", artist = "Artista",
        artistId = 1, album = "Álbum", albumId = 2, durationMs = 200_000, trackNumber = id.toInt(), year = 1999,
        folderPath = "/storage/usb/Música", fileName = "$id.mp3", mimeType = "audio/mpeg", dateAddedSec = 0, sizeBytes = 1,
    )

    @Before
    fun setUp() {
        JancarStatus.reset()
    }

    @After
    fun tearDown() {
        JancarStatus.reset()
    }

    private fun installFakeService() {
        val intent = JancarMediaClient.serviceIntent()
        val component = ComponentName(JancarProtocol.SERVICE_PACKAGE, "com.jancar.services.media.MediaService")
        @Suppress("DEPRECATION")
        shadowOf(context.packageManager).addResolveInfoForIntent(
            intent,
            ResolveInfo().apply {
                serviceInfo = ServiceInfo().apply {
                    packageName = component.packageName
                    name = component.className
                }
            },
        )
        shadowOf(context).setComponentNameAndServiceForBindServiceForIntent(intent, component, fake)
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(10)
        }
        assertWithMessage(what).that(condition()).isTrue()
    }

    /** El servicio del estéreo manda una orden a la app. */
    private fun send(callback: IBinder, code: Int, write: Parcel.() -> Unit = {}) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(JancarProtocol.CALLBACK_DESCRIPTOR)
            data.write()
            assertThat(callback.transact(code, data, reply, 0)).isTrue()
            reply.readException()
        } finally {
            data.recycle()
            reply.recycle()
        }
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun `sin el servicio del estereo la app funciona como siempre`() {
        val controller = Robolectric.buildService(PlaybackService::class.java)
        controller.create()
        assertThat(JancarStatus.link).isEqualTo(JancarStatus.Link.ABSENT)
        controller.destroy()
    }

    @Test
    fun `se registra al reproducir, obedece al volante y suelta la fuente al cerrar`() {
        installFakeService()
        val controller = Robolectric.buildService(PlaybackService::class.java)
        val player = controller.create().get().sessionForTest()!!.player
        waitFor("conectado") { JancarStatus.link == JancarStatus.Link.CONNECTED }

        player.setMediaItems(listOf(track(1), track(2)).map(MediaItemMapper::toMediaItem))
        player.play()
        waitFor("registro") { fake.last(JancarProtocol.TX_OPEN) != null }

        val open = fake.last(JancarProtocol.TX_OPEN)!!
        assertThat(open.type).isEqualTo(JancarProtocol.TYPE_THIRD_PARTY)
        assertThat(open.text.single()).isEqualTo(context.packageName)
        val callback = open.callback!!
        assertThat(callback.interfaceDescriptor).isEqualTo(JancarProtocol.CALLBACK_DESCRIPTOR)
        assertThat(JancarStatus.registered).isTrue()

        // Canción publicada (sin carátula: la URI de prueba no existe).
        waitFor("datos de la canción") { fake.last(JancarProtocol.TX_SET_MEDIA_INFO) != null }
        val info = fake.last(JancarProtocol.TX_SET_MEDIA_INFO)!!
        assertThat(info.type).isEqualTo(JancarProtocol.TYPE_THIRD_PARTY)
        assertThat(info.text).containsExactly("Canción 1", "Artista").inOrder()
        assertThat(info.ints).containsExactly(0, 0, 0, 1, 2, 0).inOrder()
        waitFor("estado sonando") { fake.last(JancarProtocol.TX_SET_MEDIA_STATE)?.ints?.first() == JancarProtocol.STATE_PLAYING }

        // Mientras el puente manda, la app no pide el foco de audio (lo pide el estéreo por ella).
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        assertThat(shadowOf(audio).lastAudioFocusRequest).isNull()

        // Volante: siguiente.
        send(callback, 9)
        assertThat(player.currentMediaItemIndex).isEqualTo(1)

        // Llamada entrante: pausa temporal y vuelta.
        send(callback, 1)
        assertThat(player.playWhenReady).isFalse()
        send(callback, 2)
        assertThat(player.playWhenReady).isTrue()

        // Atenuación del navegador.
        send(callback, 8) { writeFloat(0.4f) }
        assertThat(player.volume).isWithin(0.001f).of(0.4f)
        send(callback, 8) { writeFloat(1f) }

        // Apagar el auto: detener + salir del sistema; la app no se cierra y al encender sigue.
        send(callback, 7)
        send(callback, 6) { writeInt(JancarProtocol.QUIT_SYSTEM) }
        assertThat(player.playWhenReady).isFalse()
        send(callback, 2)
        assertThat(player.playWhenReady).isTrue()
        // Al volver a sonar tras «detener» se registra de nuevo.
        waitFor("nuevo registro") { fake.calls.count { it.code == JancarProtocol.TX_OPEN } == 2 }

        assertThat(JancarStatus.summary()).contains("siguiente×1")

        controller.destroy()
        assertThat(fake.last(JancarProtocol.TX_CLOSE)?.type).isEqualTo(JancarProtocol.TYPE_THIRD_PARTY)
    }

    @Test
    fun `cerrar desde la barra de estado pausa y suelta la fuente`() {
        installFakeService()
        val controller = Robolectric.buildService(PlaybackService::class.java)
        val player = controller.create().get().sessionForTest()!!.player
        waitFor("conectado") { JancarStatus.link == JancarStatus.Link.CONNECTED }
        player.setMediaItems(listOf(track(1)).map(MediaItemMapper::toMediaItem))
        player.play()
        waitFor("registro") { fake.last(JancarProtocol.TX_OPEN) != null }

        send(fake.last(JancarProtocol.TX_OPEN)!!.callback!!, 6) { writeInt(1) }
        assertThat(player.playWhenReady).isFalse()
        waitFor("fuente soltada") { fake.last(JancarProtocol.TX_CLOSE) != null }
        assertThat(JancarStatus.registered).isFalse()
        controller.destroy()
    }
}
