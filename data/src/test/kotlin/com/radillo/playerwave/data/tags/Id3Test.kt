package com.radillo.playerwave.data.tags

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import kotlin.random.Random

/**
 * El editor escribe en los MP3 del usuario: estas pruebas garantizan que el **audio queda
 * idéntico byte a byte**, que los marcos no editados se conservan y que se leen bien las
 * etiquetas de todas las versiones (2.2, 2.3, 2.4) y la ID3v1.
 */
class Id3Test {

    /** «Audio» que empieza como una trama MPEG real, con contenido pseudoaleatorio estable. */
    private val audio: ByteArray = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x64) + Random(42).nextBytes(5_000)

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + Random(7).nextBytes(300)

    private fun v1(title: String = "Viejo"): ByteArray {
        val b = ByteArray(128)
        "TAG".toByteArray().copyInto(b)
        title.toByteArray(Charsets.ISO_8859_1).copyInto(b, 3)
        "Comentario".toByteArray().copyInto(b, 97)
        return b
    }

    private fun edit(
        title: String = "Nuevo título",
        artist: String = "Artista",
        album: String = "Álbum",
        year: Int? = 1999,
        track: Int? = 4,
        cover: CoverChange = CoverChange.Keep,
    ) = TagEdit(title, artist, album, year, track, cover)

    private fun rewrite(input: ByteArray, e: TagEdit): ByteArray {
        val out = ByteArrayOutputStream()
        Id3.rewrite(ArraySource(input), e, out)
        return out.toByteArray()
    }

    private fun audioOf(file: ByteArray): ByteArray {
        val layout = Id3.read(ArraySource(file))
        return file.copyOfRange(layout.audioStart.toInt(), layout.audioEnd.toInt())
    }

    private fun framesOf(file: ByteArray) = Id3.read(ArraySource(file)).frames

    // ---- Etiquetas construidas a mano en cada versión ----

    private fun v24Tag(frames: List<Pair<String, ByteArray>>, footer: Boolean = false): ByteArray {
        val body = ByteArrayOutputStream()
        frames.forEach { (id, data) ->
            body.write(id.toByteArray())
            body.write(Id3.synchsafeBytes(data.size))
            body.write(byteArrayOf(0, 0))
            body.write(data)
        }
        val b = body.toByteArray()
        val header = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 4, 0, if (footer) 0x10 else 0) + Id3.synchsafeBytes(b.size)
        val foot = if (footer) byteArrayOf('3'.code.toByte(), 'D'.code.toByte(), 'I'.code.toByte(), 4, 0, 0x10) + Id3.synchsafeBytes(b.size) else ByteArray(0)
        return header + b + foot
    }

    private fun v22Tag(frames: List<Pair<String, ByteArray>>): ByteArray {
        val body = ByteArrayOutputStream()
        frames.forEach { (id, data) ->
            body.write(id.toByteArray())
            body.write(byteArrayOf((data.size shr 16).toByte(), (data.size shr 8).toByte(), data.size.toByte()))
            body.write(data)
        }
        val b = body.toByteArray()
        return byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 2, 0, 0) + Id3.synchsafeBytes(b.size) + b
    }

    // ---- Pruebas ----

    @Test
    fun `sin etiqueta previa se crea una y el audio no cambia`() {
        val out = rewrite(audio, edit())
        assertArrayEquals(audio, audioOf(out))
        val frames = framesOf(out)
        assertEquals("Nuevo título", Id3.text(frames, "TIT2"))
        assertEquals("Artista", Id3.text(frames, "TPE1"))
        assertEquals("Álbum", Id3.text(frames, "TALB"))
        assertEquals("1999", Id3.text(frames, "TYER"))
        assertEquals("4", Id3.text(frames, "TRCK"))
    }

    @Test
    fun `los marcos no editados, la portada y el audio se conservan y la ID3v1 se actualiza`() {
        val comment = Id3Frame("COMM", byteArrayOf(0) + "spa".toByteArray() + byteArrayOf(0) + "Muy buena".toByteArray())
        val custom = Id3.textFrame("TXXX", "Clave\u0000Valor")
        val original = Id3.buildTag(
            listOf(Id3.textFrame("TIT2", "Viejo"), Id3.textFrame("TPE1", "Otro"), comment, custom, Id3.coverFrame(jpeg, "image/jpeg")),
        ) + audio + v1()
        val out = rewrite(original, edit(title = "Canción nueva"))
        assertArrayEquals(audio, audioOf(out))
        val frames = framesOf(out)
        assertEquals("Canción nueva", Id3.text(frames, "TIT2"))
        assertEquals(1, frames.count { it.id == "TIT2" })
        assertTrue(frames.contains(comment))
        assertTrue(frames.contains(custom))
        assertArrayEquals(jpeg, Id3.cover(frames))
        val layout = Id3.read(ArraySource(out))
        val tail = layout.v1!!
        assertEquals("Canción nueva", String(tail, 3, 30, Charsets.ISO_8859_1).trimEnd('\u0000'))
        assertEquals("Comentario", String(tail, 97, 10, Charsets.ISO_8859_1))
        assertEquals(4, tail[126].toInt())
    }

    @Test
    fun `cambiar y quitar la portada`() {
        val original = Id3.buildTag(listOf(Id3.coverFrame(byteArrayOf(1, 2, 3), "image/png"))) + audio
        val replaced = rewrite(original, edit(cover = CoverChange.Replace(jpeg, "image/jpeg")))
        assertArrayEquals(jpeg, Id3.cover(framesOf(replaced)))
        assertEquals(1, framesOf(replaced).count { it.id == "APIC" })
        val removed = rewrite(replaced, edit(cover = CoverChange.Remove))
        assertNull(Id3.cover(framesOf(removed)))
        assertArrayEquals(audio, audioOf(removed))
    }

    @Test
    fun `ID3v2_4 con UTF-8, pie y fecha TDRC se convierte a 2_3`() {
        val title = "Canción ñ 日本"
        val tag = v24Tag(
            listOf(
                "TIT2" to (byteArrayOf(3) + title.toByteArray(Charsets.UTF_8)),
                "TDRC" to (byteArrayOf(3) + "2001-05-02".toByteArray()),
                "TCON" to (byteArrayOf(3) + "Rock".toByteArray()),
            ),
            footer = true,
        )
        val original = tag + audio
        val layout = Id3.read(ArraySource(original))
        assertEquals(tag.size.toLong(), layout.audioStart)
        assertEquals(title, Id3.text(layout.frames, "TIT2"))
        assertEquals("Rock", Id3.text(layout.frames, "TCON"))

        val out = rewrite(original, edit(title = title, year = 2001))
        assertArrayEquals(audio, audioOf(out))
        val frames = framesOf(out)
        assertEquals(title, Id3.text(frames, "TIT2"))
        assertEquals(1, frames.first { it.id == "TIT2" }.data[0].toInt()) // UTF-16: válido en 2.3
        assertEquals("Rock", Id3.text(frames, "TCON"))
        assertEquals("2001", Id3.text(frames, "TYER"))
        assertTrue(frames.none { it.id == "TDRC" })
    }

    @Test
    fun `ID3v2_2 se convierte, incluida la portada PIC`() {
        val pic = byteArrayOf(0) + "JPG".toByteArray() + byteArrayOf(3, 0) + jpeg
        val original = v22Tag(listOf("TT2" to (byteArrayOf(0) + "Viejo".toByteArray()), "TP1" to (byteArrayOf(0) + "Alguien".toByteArray()), "PIC" to pic)) + audio
        val layout = Id3.read(ArraySource(original))
        assertEquals("Viejo", Id3.text(layout.frames, "TIT2"))
        assertEquals("Alguien", Id3.text(layout.frames, "TPE1"))
        assertArrayEquals(jpeg, Id3.cover(layout.frames))
        val out = rewrite(original, edit())
        assertArrayEquals(audio, audioOf(out))
        assertArrayEquals(jpeg, Id3.cover(framesOf(out)))
    }

    @Test
    fun `etiqueta 2_3 desincronizada`() {
        // Un marco cuyo texto contiene 0xFF seguido de 0x00 añadido por la desincronización.
        val frameData = byteArrayOf(0, 'A'.code.toByte(), 0xFF.toByte(), 0x00, 'B'.code.toByte())
        val body = "TIT2".toByteArray() + Id3.int32Bytes(4) + byteArrayOf(0, 0) + frameData
        val tag = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0x80.toByte()) + Id3.synchsafeBytes(body.size) + body
        val frames = Id3.read(ArraySource(tag + audio)).frames
        assertEquals("AÿB", Id3.text(frames, "TIT2"))
    }

    @Test
    fun `se conserva el total de pistas`() {
        val original = Id3.buildTag(listOf(Id3.textFrame("TRCK", "3/12"))) + audio
        assertEquals("5/12", Id3.text(framesOf(rewrite(original, edit(track = 5))), "TRCK"))
    }

    @Test
    fun `campos vacios se eliminan`() {
        val original = Id3.buildTag(listOf(Id3.textFrame("TALB", "Viejo"), Id3.textFrame("TYER", "1980"), Id3.textFrame("TRCK", "2"))) + audio
        val frames = framesOf(rewrite(original, edit(album = "", year = null, track = null)))
        assertNull(Id3.text(frames, "TALB"))
        assertNull(Id3.text(frames, "TYER"))
        assertNull(Id3.text(frames, "TRCK"))
    }

    @Test
    fun `etiquetas repetidas al principio se quitan`() {
        val stale = Id3.buildTag(listOf(Id3.textFrame("TIT2", "Antigua")), padding = 0)
        val first = Id3.buildTag(listOf(Id3.textFrame("TIT2", "Vigente")), padding = 10)
        val original = first + stale + audio
        val layout = Id3.read(ArraySource(original))
        assertEquals("Vigente", Id3.text(layout.frames, "TIT2"))
        val out = rewrite(original, edit())
        assertArrayEquals(audio, audioOf(out))
        assertEquals(-1, String(out, Charsets.ISO_8859_1).indexOf("Antigua"))
    }

    @Test
    fun `una etiqueta mas grande que el archivo no se toca`() {
        val broken = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0) + Id3.synchsafeBytes(1_000_000) + audio
        try {
            rewrite(broken, edit())
            fail("Debía rechazar el archivo")
        } catch (_: InvalidTagException) {
        }
    }

    @Test
    fun `reescribir dos veces da el mismo audio y desde un archivo real`() {
        val original = Id3.buildTag(listOf(Id3.textFrame("TIT2", "x"))) + audio + v1()
        val tmp = File.createTempFile("id3", ".mp3").apply { writeBytes(original) }
        try {
            val once = RandomAccessFile(tmp, "r").use { raf ->
                ByteArrayOutputStream().also { Id3.rewrite(FileSource(raf), edit(), it) }.toByteArray()
            }
            val twice = rewrite(once, edit(title = "Otra vez"))
            assertArrayEquals(audio, audioOf(twice))
            assertEquals("Otra vez", Id3.text(framesOf(twice), "TIT2"))
            assertNotNull(Id3.read(ArraySource(twice)).v1)
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun `reconoce MP3 por tipo o extension`() {
        assertTrue(Id3.isMp3("audio/mpeg", "a.bin"))
        assertTrue(Id3.isMp3("", "Canción.MP3"))
        assertTrue(!Id3.isMp3("audio/flac", "a.flac"))
    }
}
