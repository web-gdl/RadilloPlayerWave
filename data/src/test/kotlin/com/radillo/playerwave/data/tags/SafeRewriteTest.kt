package com.radillo.playerwave.data.tags

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlin.random.Random

/** Reescritura en el sitio: si algo falla, el MP3 del usuario debe quedar exactamente como estaba. */
class SafeRewriteTest {

    private val audio = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x64) + Random(1).nextBytes(200_000)
    private val bigCover = Random(2).nextBytes(150_000)
    private val work: File = Files.createTempDirectory("work").toFile()
    private val rescue: File = Files.createTempDirectory("rescue").toFile()

    private fun mp3(): File = File.createTempFile("song", ".mp3").apply {
        writeBytes(Id3.buildTag(listOf(Id3.textFrame("TIT2", "Viejo"), Id3.coverFrame(bigCover, "image/jpeg"))) + audio)
    }

    private fun edit(cover: CoverChange = CoverChange.Keep) = TagEdit("Nuevo", "Artista", "Álbum", 2000, 1, cover)

    private fun audioOf(bytes: ByteArray): ByteArray {
        val l = Id3.read(ArraySource(bytes))
        return bytes.copyOfRange(l.audioStart.toInt(), l.audioEnd.toInt())
    }

    @Test
    fun `reescribe en el sitio, incluso cuando el archivo se acorta`() {
        val file = mp3()
        val newSize = RandomAccessFile(file, "rw").use { raf ->
            SafeRewrite.run(raf.channel, raf.channel, work, rescue, edit(cover = CoverChange.Remove))
        }
        val bytes = file.readBytes()
        assertEquals(newSize, bytes.size.toLong())
        assertArrayEquals(audio, audioOf(bytes))
        assertEquals("Nuevo", Id3.text(Id3.read(ArraySource(bytes)).frames, "TIT2"))
        assertTrue(work.listFiles()!!.isEmpty()) // sin temporales
        file.delete()
    }

    @Test
    fun `un fallo a medio escribir restaura el original byte a byte`() {
        val file = mp3()
        val original = file.readBytes()
        try {
            RandomAccessFile(file, "rw").use { raf ->
                SafeRewrite.run(raf.channel, raf.channel, work, rescue, edit(), afterWrite = { throw IOException("corte de corriente simulado") })
            }
            fail("Debía propagar el error")
        } catch (_: IOException) {
        }
        assertArrayEquals(original, file.readBytes())
        assertTrue(work.listFiles()!!.isEmpty())
        file.delete()
    }

    @Test
    fun `una etiqueta danada no toca el archivo`() {
        val file = File.createTempFile("broken", ".mp3").apply {
            writeBytes(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0) + Id3.synchsafeBytes(9_000_000) + audio)
        }
        val original = file.readBytes()
        try {
            RandomAccessFile(file, "rw").use { raf -> SafeRewrite.run(raf.channel, raf.channel, work, rescue, edit()) }
            fail("Debía rechazarla")
        } catch (_: InvalidTagException) {
        }
        assertArrayEquals(original, file.readBytes())
        file.delete()
    }
}
