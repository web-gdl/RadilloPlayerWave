package com.radillo.playerwave.data.tags

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlin.random.Random

/** Reescritura en el sitio: si algo falla o se corta la corriente, el MP3 no se pierde. */
class SafeRewriteTest {

    private val audio = MpegFixtures.frames(480, seed = 1)
    private val bigCover = Random(2).nextBytes(150_000)
    private val dir: File = Files.createTempDirectory("tags").toFile()
    private val work = File(dir, "work")
    private val journal = Journal(File(dir, "journal.txt"))

    /** Simula que el sistema mata el proceso: no es una Exception, así que nada la recoge. */
    private class ProcessKilled : Error()

    private fun mp3(padding: Int = Id3.PADDING): File = File.createTempFile("song", ".mp3", dir).apply {
        writeBytes(Id3.buildTag(listOf(Id3.textFrame("TIT2", "Viejo"), Id3.coverFrame(bigCover, "image/jpeg")), padding) + audio)
    }

    private fun edit(title: String = "Nuevo", cover: CoverChange = CoverChange.Keep) = TagEdit(title, "Artista", "Álbum", 2000, 1, cover)

    private fun audioOf(bytes: ByteArray): ByteArray {
        val l = Id3.read(ArraySource(bytes))
        return bytes.copyOfRange(l.audioStart.toInt(), l.audioEnd.toInt())
    }

    private fun run(file: File, e: TagEdit, afterWrite: (RandomAccessFile) -> Unit = {}): Long =
        RandomAccessFile(file, "rw").use { raf ->
            SafeRewrite.run(raf.channel, raf.channel, work, journal, file.toURI().toString(), e, afterWrite = { afterWrite(raf) })
        }

    private fun opener(file: File): (String) -> SafeRewrite.Channels = {
        val raf = RandomAccessFile(file, "rw")
        SafeRewrite.Channels(raf.channel, raf.channel, sync = { raf.fd.sync() }, close = { raf.close() })
    }

    private fun workIsEmpty() = (work.listFiles() ?: emptyArray()).isEmpty()

    @Test
    fun `un cambio de texto se escribe en el hueco con el mismo tamano y el audio intacto`() {
        val file = mp3(padding = 4_096)
        val before = file.length()
        val newSize = run(file, edit(title = "Título más largo que el anterior"))
        val bytes = file.readBytes()
        assertEquals(before, newSize)
        assertEquals(before, bytes.size.toLong())
        assertArrayEquals(audio, audioOf(bytes))
        assertEquals("Título más largo que el anterior", Id3.text(Id3.read(ArraySource(bytes)).frames, "TIT2"))
        assertNull(journal.read())
        assertTrue(workIsEmpty())
    }

    @Test
    fun `reescritura completa cuando no cabe, incluso si el archivo se acorta`() {
        val file = mp3(padding = 0)
        run(file, edit(cover = CoverChange.Replace(Random(5).nextBytes(200_000), "image/jpeg")))
        assertArrayEquals(audio, audioOf(file.readBytes()))
        val shrunk = run(file, edit(cover = CoverChange.Remove))
        val bytes = file.readBytes()
        assertEquals(shrunk, bytes.size.toLong())
        assertArrayEquals(audio, audioOf(bytes))
        assertNull(Id3.cover(Id3.read(ArraySource(bytes)).frames))
        assertNull(journal.read())
        assertTrue(workIsEmpty())
    }

    @Test
    fun `un error a medio escribir restaura el original byte a byte`() {
        val file = mp3(padding = 0)
        val original = file.readBytes()
        try {
            run(file, edit(cover = CoverChange.Remove)) { throw IOException("fallo de escritura simulado") }
            fail("Debía propagar el error")
        } catch (_: IOException) {
        }
        assertArrayEquals(original, file.readBytes())
        assertNull(journal.read())
        assertTrue(workIsEmpty())
    }

    @Test
    fun `corte de corriente a medio escribir se repara al arrancar`() {
        val file = mp3(padding = 0)
        val original = file.readBytes()
        try {
            run(file, edit(cover = CoverChange.Remove)) { raf ->
                raf.channel.truncate(raf.length() / 2) // archivo a medio escribir
                throw ProcessKilled()
            }
            fail("Debía morir")
        } catch (_: ProcessKilled) {
        }
        val pending = journal.read()
        assertNotNull(pending)
        assertTrue(File(pending!!.backupPath).exists())
        // Mientras hay algo pendiente no se permite otra edición.
        try {
            run(file, edit())
            fail("Debía exigir la recuperación primero")
        } catch (_: PendingRecoveryException) {
        }
        assertEquals(SafeRewrite.Recovery.RESTORED, SafeRewrite.recover(journal, opener(file)))
        assertArrayEquals(original, file.readBytes())
        assertNull(journal.read())
        assertTrue(workIsEmpty())
    }

    @Test
    fun `corte de corriente justo despues de escribir conserva lo nuevo`() {
        val file = mp3(padding = 4_096)
        try {
            run(file, edit(title = "Llegó a escribirse")) { throw ProcessKilled() }
        } catch (_: ProcessKilled) {
        }
        assertEquals(SafeRewrite.Recovery.COMPLETED, SafeRewrite.recover(journal, opener(file)))
        assertEquals("Llegó a escribirse", Id3.text(Id3.read(ArraySource(file.readBytes())).frames, "TIT2"))
        assertTrue(workIsEmpty())
    }

    @Test
    fun `sin acceso al archivo la recuperacion espera`() {
        val file = mp3(padding = 0)
        try {
            run(file, edit(cover = CoverChange.Remove)) { throw ProcessKilled() }
        } catch (_: ProcessKilled) {
        }
        assertEquals(SafeRewrite.Recovery.PENDING, SafeRewrite.recover(journal, { null }))
        assertNotNull(journal.read())
        assertEquals(SafeRewrite.Recovery.COMPLETED, SafeRewrite.recover(journal, opener(file)))
    }

    @Test
    fun `una etiqueta danada o un archivo que no es MP3 no se tocan`() {
        val broken = File.createTempFile("broken", ".mp3", dir).apply {
            writeBytes(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0) + Id3.synchsafeBytes(9_000_000) + audio)
        }
        val before = broken.readBytes()
        try {
            run(broken, edit())
            fail("Debía rechazarla")
        } catch (_: InvalidTagException) {
        }
        assertArrayEquals(before, broken.readBytes())

        val m4a = File.createTempFile("fake", ".mp3", dir).apply {
            writeBytes(byteArrayOf(0, 0, 0, 0x20) + "ftypM4A ".toByteArray() + Random(6).nextBytes(50_000))
        }
        val m4aBefore = m4a.readBytes()
        try {
            run(m4a, edit())
            fail("Debía rechazarlo")
        } catch (_: NotMp3Exception) {
        }
        assertArrayEquals(m4aBefore, m4a.readBytes())
        assertNull(journal.read())
        assertTrue(workIsEmpty())
    }
}
