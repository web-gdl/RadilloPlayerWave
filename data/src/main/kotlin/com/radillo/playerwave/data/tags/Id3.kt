package com.radillo.playerwave.data.tags

import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.charset.Charset

/** Fuente de bytes con acceso aleatorio: archivo local en la app, array en las pruebas. */
interface ByteSource {
    val size: Long
    /** Lee hasta [length] bytes desde [position]; devuelve los leídos (0 al final). */
    fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int
}

class ArraySource(private val bytes: ByteArray) : ByteSource {
    override val size: Long get() = bytes.size.toLong()
    override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (position >= bytes.size) return 0
        val n = minOf(length.toLong(), bytes.size - position).toInt()
        System.arraycopy(bytes, position.toInt(), buffer, offset, n)
        return n
    }
}

class FileSource(private val file: RandomAccessFile) : ByteSource {
    override val size: Long get() = file.length()
    override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        file.seek(position)
        val n = file.read(buffer, offset, length)
        return if (n < 0) 0 else n
    }
}

/** Lectura posicional de un descriptor (archivo, MediaStore o carpeta concedida); no mueve su posición. */
class ChannelSource(private val channel: java.nio.channels.FileChannel) : ByteSource {
    override val size: Long get() = channel.size()
    override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        val n = channel.read(java.nio.ByteBuffer.wrap(buffer, offset, length), position)
        return if (n < 0) 0 else n
    }
}

/** Un marco ID3v2 ya normalizado a la forma de la versión 2.3 (id de 4 letras, sin indicadores). */
class Id3Frame(val id: String, val data: ByteArray) {
    override fun equals(other: Any?): Boolean = other is Id3Frame && other.id == id && other.data.contentEquals(data)
    override fun hashCode(): Int = id.hashCode() * 31 + data.contentHashCode()
    override fun toString(): String = "Id3Frame($id, ${data.size} bytes)"
}

/** Qué hacer con la portada incrustada. */
sealed interface CoverChange {
    data object Keep : CoverChange
    data object Remove : CoverChange
    class Replace(val bytes: ByteArray, val mimeType: String) : CoverChange
}

/** Etiquetas de una canción. Los campos vacíos o nulos se eliminan del archivo. */
data class TagEdit(
    val title: String,
    val artist: String,
    val album: String,
    val year: Int?,
    val trackNumber: Int?,
    val cover: CoverChange = CoverChange.Keep,
)

/** Estructura de un MP3: etiqueta ID3v2 (marcos), dónde empieza y acaba el audio, y la ID3v1. */
class Id3Layout(
    val frames: List<Id3Frame>,
    val audioStart: Long,
    val audioEnd: Long,
    val v1: ByteArray?,
)

class InvalidTagException(message: String) : Exception(message)

/**
 * Lectura y escritura de etiquetas ID3 en MP3, en Kotlin puro (sin bibliotecas ni Android).
 *
 * - Lee ID3v2.2, 2.3 y 2.4 (desincronización, cabecera extendida, pie, tamaños «synchsafe»,
 *   UTF-8) y los convierte a la forma 2.3. Los marcos que no se editan **se conservan**.
 * - Escribe ID3v2.3, la versión que entienden todos los reproductores (también los de coche),
 *   con texto en ISO-8859-1 o, si hace falta, UTF-16.
 * - El audio se copia **byte a byte** sin tocarlo; la ID3v1 del final, si existe, se actualiza.
 * - Las etiquetas ID3v2 repetidas al principio (de editores que no borran la anterior) se quitan.
 */
object Id3 {

    private val LATIN1: Charset = Charsets.ISO_8859_1
    private const val MAX_TAG_BYTES = 64 * 1024 * 1024
    const val PADDING = 1024

    /** Marcos que el editor reemplaza. */
    private val EDITED = setOf("TIT2", "TPE1", "TALB", "TYER", "TDRC", "TRCK")

    private val V22_TO_V23 = mapOf(
        "TT1" to "TIT1", "TT2" to "TIT2", "TT3" to "TIT3", "TP1" to "TPE1", "TP2" to "TPE2", "TP3" to "TPE3",
        "TP4" to "TPE4", "TAL" to "TALB", "TYE" to "TYER", "TRK" to "TRCK", "TPA" to "TPOS", "TCO" to "TCON",
        "TCM" to "TCOM", "TEN" to "TENC", "TCR" to "TCOP", "TBP" to "TBPM", "TLA" to "TLAN", "TPB" to "TPUB",
        "COM" to "COMM", "ULT" to "USLT", "TXX" to "TXXX",
    )

    fun isMp3(mimeType: String, fileName: String): Boolean =
        mimeType.equals("audio/mpeg", ignoreCase = true) || mimeType.equals("audio/mp3", ignoreCase = true) ||
            fileName.endsWith(".mp3", ignoreCase = true)

    // ---- Lectura ----

    fun read(source: ByteSource): Id3Layout {
        val size = source.size
        var frames: List<Id3Frame> = emptyList()
        var audioStart = 0L
        var first = true
        // Una o varias etiquetas ID3v2 seguidas al principio: vale la primera, las demás se descartan.
        while (true) {
            val header = readExact(source, audioStart, 10) ?: break
            if (!isHeader(header)) break
            val major = header[3].toInt()
            val flags = header[5].toInt() and 0xFF
            val tagSize = synchsafe(header, 6)
            val footer = if (major == 4 && flags and 0x10 != 0) 10 else 0
            val end = audioStart + 10 + tagSize + footer
            if (end > size) throw InvalidTagException("La etiqueta ID3 dice medir más que el archivo")
            if (first) {
                if (tagSize > MAX_TAG_BYTES) throw InvalidTagException("Etiqueta ID3 demasiado grande")
                val body = readExact(source, audioStart + 10, tagSize) ?: throw InvalidTagException("No se pudo leer la etiqueta")
                frames = parseBody(major, flags, body)
                first = false
            }
            audioStart = end
        }
        var audioEnd = size
        var v1: ByteArray? = null
        if (size - audioStart >= 128) {
            val tail = readExact(source, size - 128, 128)
            if (tail != null && tail[0] == 'T'.code.toByte() && tail[1] == 'A'.code.toByte() && tail[2] == 'G'.code.toByte()) {
                v1 = tail
                audioEnd = size - 128
            }
        }
        return Id3Layout(frames, audioStart, audioEnd, v1)
    }

    private fun isHeader(h: ByteArray): Boolean =
        h[0] == 'I'.code.toByte() && h[1] == 'D'.code.toByte() && h[2] == '3'.code.toByte() &&
            h[3].toInt() in 2..4 && (6..9).all { h[it].toInt() and 0x80 == 0 }

    private fun parseBody(major: Int, flags: Int, rawBody: ByteArray): List<Id3Frame> {
        var body = rawBody
        if (flags and 0x80 != 0 && major <= 3) body = deUnsync(body)
        if (major == 2 && flags and 0x40 != 0) return emptyList() // compresión de 2.2: sin estándar
        var pos = 0
        if (flags and 0x40 != 0 && major >= 3 && body.size >= 4) {
            pos = if (major == 3) 4 + int32(body, 0) else synchsafe(body, 0)
            if (pos < 0 || pos > body.size) return emptyList()
        }
        val tagUnsync = major == 4 && flags and 0x80 != 0
        val frames = ArrayList<Id3Frame>()
        while (true) {
            if (major == 2) {
                if (pos + 6 > body.size || body[pos].toInt() == 0) break
                val id = String(body, pos, 3, LATIN1)
                if (!id.all { it in 'A'..'Z' || it in '0'..'9' }) break
                val len = ((body[pos + 3].toInt() and 0xFF) shl 16) or ((body[pos + 4].toInt() and 0xFF) shl 8) or (body[pos + 5].toInt() and 0xFF)
                if (pos + 6 + len > body.size) break
                val data = body.copyOfRange(pos + 6, pos + 6 + len)
                pos += 6 + len
                convertV22(id, data)?.let(frames::add)
            } else {
                if (pos + 10 > body.size || body[pos].toInt() == 0) break
                val id = String(body, pos, 4, LATIN1)
                if (!id.all { it in 'A'..'Z' || it in '0'..'9' }) break
                val len = if (major == 4 && (0..3).all { body[pos + 4 + it].toInt() and 0x80 == 0 }) synchsafe(body, pos + 4) else int32(body, pos + 4)
                if (len < 0 || pos + 10 + len > body.size) break
                val formatFlags = body[pos + 9].toInt() and 0xFF
                var data = body.copyOfRange(pos + 10, pos + 10 + len)
                pos += 10 + len
                if (major == 3) {
                    if (formatFlags and 0xC0 != 0) continue // comprimido o cifrado
                    if (formatFlags and 0x20 != 0 && data.isNotEmpty()) data = data.copyOfRange(1, data.size)
                    frames += Id3Frame(id, data)
                } else {
                    if (formatFlags and 0x0C != 0) continue // comprimido o cifrado
                    var skip = 0
                    if (formatFlags and 0x40 != 0) skip += 1
                    if (formatFlags and 0x01 != 0) skip += 4
                    if (skip > data.size) continue
                    data = data.copyOfRange(skip, data.size)
                    if (formatFlags and 0x02 != 0 || tagUnsync) data = deUnsync(data)
                    convertV24(id, data)?.let(frames::add)
                }
            }
        }
        return frames
    }

    private fun convertV22(id: String, data: ByteArray): Id3Frame? {
        if (id == "PIC") {
            // PIC: [codificación][formato de 3 letras][tipo][descripción][imagen] → APIC con tipo MIME.
            if (data.size < 5) return null
            val format = String(data, 1, 3, LATIN1).uppercase()
            val mime = if (format == "PNG") "image/png" else "image/jpeg"
            val out = byteArrayOf(data[0]) + mime.toByteArray(LATIN1) + byteArrayOf(0) + data.copyOfRange(4, data.size)
            return Id3Frame("APIC", out)
        }
        val v23 = V22_TO_V23[id] ?: return null
        return Id3Frame(v23, data)
    }

    /** 2.4 → 2.3: el UTF-8 (codificación 3) no existe en 2.3. */
    private fun convertV24(id: String, data: ByteArray): Id3Frame? {
        if (data.isEmpty() || data[0].toInt() != 3) return Id3Frame(id, data)
        return when {
            id.startsWith("T") -> {
                val strings = String(data, 1, data.size - 1, Charsets.UTF_8).split('\u0000').let { parts ->
                    if (parts.size > 1 && parts.last().isEmpty()) parts.dropLast(1) else parts
                }
                val out = java.io.ByteArrayOutputStream()
                out.write(1)
                strings.forEachIndexed { i, s ->
                    if (i > 0) out.write(byteArrayOf(0, 0))
                    out.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
                    out.write(s.toByteArray(Charsets.UTF_16LE))
                }
                Id3Frame(id, out.toByteArray())
            }
            id == "APIC" -> {
                // [3][mime\0][tipo][descripción UTF-8\0][imagen] → descripción vacía en ISO-8859-1.
                val mimeEnd = indexOf(data, 0, 1)
                if (mimeEnd < 0 || mimeEnd + 2 > data.size) return null
                val descEnd = indexOf(data, 0, mimeEnd + 2)
                if (descEnd < 0) return null
                Id3Frame(id, byteArrayOf(0) + data.copyOfRange(1, mimeEnd + 2) + byteArrayOf(0) + data.copyOfRange(descEnd + 1, data.size))
            }
            else -> null // comentarios o letras en UTF-8: poco comunes; se descartan en vez de dañarlos
        }
    }

    // ---- Escritura ----

    /** Marcos resultantes de aplicar [edit] a [frames] (los no editados se conservan en su orden). */
    fun applyEdit(frames: List<Id3Frame>, edit: TagEdit): List<Id3Frame> {
        val removeCovers = edit.cover !is CoverChange.Keep
        val previousTotal = frames.firstOrNull { it.id == "TRCK" }?.let(::decodeText)?.substringAfter('/', "")?.trim().orEmpty()
        val kept = frames.filterNot { it.id in EDITED || (removeCovers && it.id == "APIC") }
        val result = ArrayList<Id3Frame>()
        if (edit.title.isNotBlank()) result += textFrame("TIT2", edit.title.trim())
        if (edit.artist.isNotBlank()) result += textFrame("TPE1", edit.artist.trim())
        if (edit.album.isNotBlank()) result += textFrame("TALB", edit.album.trim())
        edit.year?.takeIf { it > 0 }?.let { result += textFrame("TYER", it.toString().padStart(4, '0')) }
        edit.trackNumber?.takeIf { it > 0 }?.let { n ->
            result += textFrame("TRCK", if (previousTotal.isNotEmpty()) "$n/$previousTotal" else n.toString())
        }
        result += kept
        (edit.cover as? CoverChange.Replace)?.let { result += coverFrame(it.bytes, it.mimeType) }
        return result
    }

    fun textFrame(id: String, value: String): Id3Frame = Id3Frame(id, encodeText(value))

    /** ISO-8859-1 si basta (lo más compatible); si no, UTF-16 con BOM. */
    fun encodeText(value: String): ByteArray =
        if (value.all { it.code <= 0xFF }) {
            byteArrayOf(0) + value.toByteArray(LATIN1)
        } else {
            byteArrayOf(1, 0xFF.toByte(), 0xFE.toByte()) + value.toByteArray(Charsets.UTF_16LE)
        }

    /** Portada delantera (tipo 3), sin descripción. */
    fun coverFrame(bytes: ByteArray, mimeType: String): Id3Frame =
        Id3Frame("APIC", byteArrayOf(0) + mimeType.toByteArray(LATIN1) + byteArrayOf(0, 3, 0) + bytes)

    /** Etiqueta ID3v2.3 completa (cabecera + marcos + relleno). */
    fun buildTag(frames: List<Id3Frame>, padding: Int = PADDING): ByteArray {
        val body = java.io.ByteArrayOutputStream()
        frames.forEach { f ->
            body.write(f.id.toByteArray(LATIN1))
            body.write(int32Bytes(f.data.size))
            body.write(byteArrayOf(0, 0))
            body.write(f.data)
        }
        body.write(ByteArray(padding))
        val bodyBytes = body.toByteArray()
        if (bodyBytes.size >= 1 shl 28) throw InvalidTagException("Etiqueta demasiado grande")
        val header = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0) + synchsafeBytes(bodyBytes.size)
        return header + bodyBytes
    }

    /** ID3v1 con los campos editados (texto latino, 30 caracteres como máximo). */
    fun updateV1(v1: ByteArray, edit: TagEdit): ByteArray {
        val out = v1.copyOf()
        fun put(offset: Int, length: Int, value: String) {
            val bytes = value.map { if (it.code <= 0xFF) it else '?' }.joinToString("").toByteArray(LATIN1)
            for (i in 0 until length) out[offset + i] = if (i < bytes.size) bytes[i] else 0
        }
        put(3, 30, edit.title.trim())
        put(33, 30, edit.artist.trim())
        put(63, 30, edit.album.trim())
        put(93, 4, edit.year?.takeIf { it > 0 }?.toString() ?: "")
        val track = edit.trackNumber
        if (track != null && track in 1..255) {
            out[125] = 0
            out[126] = track.toByte()
        }
        return out
    }

    /**
     * Escribe en [out] el MP3 con las etiquetas editadas: nueva ID3v2.3, el audio original tal cual
     * y la ID3v1 actualizada (si existía). Devuelve el número de bytes de audio copiados.
     */
    fun rewrite(source: ByteSource, edit: TagEdit, out: OutputStream): Long {
        val layout = read(source)
        out.write(buildTag(applyEdit(layout.frames, edit)))
        val buffer = ByteArray(64 * 1024)
        var pos = layout.audioStart
        while (pos < layout.audioEnd) {
            val want = minOf(buffer.size.toLong(), layout.audioEnd - pos).toInt()
            val n = source.read(pos, buffer, 0, want)
            if (n <= 0) throw InvalidTagException("Lectura incompleta del audio")
            out.write(buffer, 0, n)
            pos += n
        }
        layout.v1?.let { out.write(updateV1(it, edit)) }
        return layout.audioEnd - layout.audioStart
    }

    // ---- Consulta ----

    /** Texto del primer marco con [id], o null. */
    fun text(frames: List<Id3Frame>, id: String): String? = frames.firstOrNull { it.id == id }?.let(::decodeText)

    fun decodeText(frame: Id3Frame): String? {
        val d = frame.data
        if (d.isEmpty()) return null
        val payload = d.copyOfRange(1, d.size)
        val text = when (d[0].toInt()) {
            0 -> String(payload, LATIN1)
            1 -> String(payload, Charsets.UTF_16) // usa el BOM
            2 -> String(payload, Charsets.UTF_16BE)
            3 -> String(payload, Charsets.UTF_8)
            else -> return null
        }
        return text.substringBefore('\u0000').trim()
    }

    /** Imagen de la portada incrustada (la delantera si hay varias), o null. */
    fun cover(frames: List<Id3Frame>): ByteArray? {
        val pictures = frames.filter { it.id == "APIC" }.mapNotNull(::parsePicture)
        return (pictures.firstOrNull { it.first == 3 } ?: pictures.firstOrNull())?.second
    }

    private fun parsePicture(frame: Id3Frame): Pair<Int, ByteArray>? {
        val d = frame.data
        if (d.size < 4) return null
        val encoding = d[0].toInt()
        val mimeEnd = indexOf(d, 0, 1)
        if (mimeEnd < 0 || mimeEnd + 2 > d.size) return null
        val type = d[mimeEnd + 1].toInt() and 0xFF
        var p = mimeEnd + 2
        if (encoding == 1 || encoding == 2) {
            while (p + 1 < d.size && !(d[p].toInt() == 0 && d[p + 1].toInt() == 0)) p += 2
            p += 2
        } else {
            while (p < d.size && d[p].toInt() != 0) p++
            p += 1
        }
        if (p > d.size) return null
        return type to d.copyOfRange(p, d.size)
    }

    // ---- Utilidades ----

    private fun readExact(source: ByteSource, position: Long, length: Int): ByteArray? {
        if (position < 0 || position + length > source.size) return null
        val buf = ByteArray(length)
        var off = 0
        while (off < length) {
            val n = source.read(position + off, buf, off, length - off)
            if (n <= 0) return null
            off += n
        }
        return buf
    }

    /** Quita la «desincronización»: cada 0x00 que sigue a un 0xFF se elimina. */
    fun deUnsync(data: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(data.size)
        var i = 0
        while (i < data.size) {
            val b = data[i]
            out.write(b.toInt())
            if (b == 0xFF.toByte() && i + 1 < data.size && data[i + 1].toInt() == 0) i++
            i++
        }
        return out.toByteArray()
    }

    private fun indexOf(data: ByteArray, value: Int, from: Int): Int {
        for (i in from until data.size) if (data[i].toInt() == value) return i
        return -1
    }

    private fun synchsafe(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0x7F) shl 21) or ((b[o + 1].toInt() and 0x7F) shl 14) or
            ((b[o + 2].toInt() and 0x7F) shl 7) or (b[o + 3].toInt() and 0x7F)

    private fun int32(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

    fun synchsafeBytes(v: Int): ByteArray =
        byteArrayOf(((v shr 21) and 0x7F).toByte(), ((v shr 14) and 0x7F).toByte(), ((v shr 7) and 0x7F).toByte(), (v and 0x7F).toByte())

    fun int32Bytes(v: Int): ByteArray =
        byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
}
