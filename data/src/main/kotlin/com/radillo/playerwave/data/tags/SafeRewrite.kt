package com.radillo.playerwave.data.tags

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.zip.CRC32

/** Hay una reescritura anterior sin cerrar (p. ej. se cortó la corriente): primero hay que recuperarla. */
class PendingRecoveryException : IOException("Reescritura anterior pendiente de recuperar")

/** Lo que se apunta (y se sincroniza a disco) **antes** de tocar la canción. */
data class JournalEntry(
    /** Cómo volver a abrir el archivo: URI `content://` o `file://`. */
    val target: String,
    val backupPath: String,
    val originalSize: Long,
    val originalCrc: Long,
    val newSize: Long,
    val newCrc: Long,
)

/** Diario de una sola entrada en un archivo de texto, escrito de forma atómica y sincronizada. */
class Journal(private val file: File) {

    fun write(entry: JournalEntry) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(
                listOf(
                    "target\t${entry.target}", "backup\t${entry.backupPath}", "originalSize\t${entry.originalSize}",
                    "originalCrc\t${entry.originalCrc}", "newSize\t${entry.newSize}", "newCrc\t${entry.newCrc}",
                ).joinToString("\n").toByteArray(Charsets.UTF_8),
            )
            out.fd.sync()
        }
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw IOException("No se pudo guardar el diario")
        }
    }

    fun read(): JournalEntry? = try {
        if (!file.exists()) {
            null
        } else {
            val map = file.readLines(Charsets.UTF_8).mapNotNull { line ->
                val i = line.indexOf('\t')
                if (i < 0) null else line.substring(0, i) to line.substring(i + 1)
            }.toMap()
            JournalEntry(
                target = map.getValue("target"),
                backupPath = map.getValue("backup"),
                originalSize = map.getValue("originalSize").toLong(),
                originalCrc = map.getValue("originalCrc").toLong(),
                newSize = map.getValue("newSize").toLong(),
                newCrc = map.getValue("newCrc").toLong(),
            )
        }
    } catch (e: Exception) {
        null
    }

    fun clear() {
        file.delete()
    }
}

/**
 * Reescritura de un MP3 **en su sitio**, a prueba de cortes de corriente. Kotlin puro sobre canales
 * de archivo, para probarla sin dispositivo.
 *
 * 1. Copia de seguridad sincronizada a disco y cálculo de su CRC.
 * 2. Plan y archivo nuevo generados desde la copia ([Id3.plan]): si la etiqueta está dañada o el
 *    contenido no es MP3, se aborta aquí, sin haber tocado nada.
 * 3. Se apunta en el [Journal] (sincronizado): destino, copia, tamaños y CRC del antes y el después.
 * 4. Escritura. Si la etiqueta nueva cabe en el hueco de la vieja, **solo se escribe ese hueco** (y
 *    la ID3v1): el audio no se mueve. Si no, se reescribe entero y se recorta.
 * 5. Verificación por CRC de todo el archivo. Si algo falla, se restaura la copia y se verifica.
 *
 * Si el proceso muere en medio (la llave del coche, sin batería), el diario queda: al arrancar,
 * [recover] mira el CRC del archivo y lo da por bueno (antes o después) o lo restaura de la copia.
 */
object SafeRewrite {

    class Channels(val input: FileChannel, val output: FileChannel, val sync: () -> Unit, val close: () -> Unit)

    enum class Recovery { NONE, COMPLETED, RESTORED, PENDING, LOST }

    /** Devuelve el tamaño nuevo del archivo. */
    fun run(
        input: FileChannel,
        output: FileChannel,
        workDir: File,
        journal: Journal,
        target: String,
        edit: TagEdit,
        sync: () -> Unit = {},
        log: (String) -> Unit = {},
        /** Solo pruebas: tras escribir y antes de sincronizar y verificar (puede lanzar para simular un fallo). */
        afterWrite: () -> Unit = {},
    ): Long {
        if (journal.read() != null) throw PendingRecoveryException()
        val originalSize = input.size()
        if (originalSize <= 0) throw IOException("Archivo vacío o no accesible")
        workDir.mkdirs()
        val backup = File.createTempFile("tag_backup_", ".mp3", workDir)
        val rewritten = File.createTempFile("tag_new_", ".mp3", workDir)
        var keepBackup = false
        try {
            FileOutputStream(backup).use { out ->
                var pos = 0L
                while (pos < originalSize) {
                    val n = input.transferTo(pos, originalSize - pos, out.channel)
                    if (n <= 0) throw IOException("Copia de seguridad incompleta")
                    pos += n
                }
                out.fd.sync()
            }
            if (backup.length() != originalSize) throw IOException("Copia de seguridad incompleta")
            val originalCrc = crc(backup)

            val plan = RandomAccessFile(backup, "r").use { raf ->
                val plan = Id3.plan(FileSource(raf), edit)
                FileOutputStream(rewritten).use { out ->
                    out.write(plan.tag)
                    copyRange(raf, plan.layout.audioStart, plan.layout.audioEnd, out)
                    plan.v1?.let(out::write)
                    out.fd.sync()
                }
                plan
            }
            val newSize = rewritten.length()
            if (newSize != plan.newSize) throw IOException("Tamaño del archivo nuevo inesperado")
            val newCrc = crc(rewritten)

            journal.write(JournalEntry(target, backup.absolutePath, originalSize, originalCrc, newSize, newCrc))
            // Mientras el diario esté abierto, la copia no se borra pase lo que pase (incluido un Error).
            keepBackup = true
            try {
                if (plan.inPlace) {
                    writeAt(output, plan.tag, 0)
                    plan.v1?.let { writeAt(output, it, originalSize - it.size) }
                } else {
                    writeAll(rewritten, output)
                    output.truncate(newSize)
                }
                afterWrite()
                sync()
                if (input.size() != newSize || crc(input, newSize) != newCrc) throw IOException("La verificación del archivo escrito falló")
            } catch (e: Exception) {
                try {
                    restore(backup, output, originalSize)
                    sync()
                    if (crc(input, originalSize) != originalCrc) throw IOException("La restauración no se pudo verificar")
                    journal.clear()
                    keepBackup = false
                    log("Editor: escritura fallida (${e.javaClass.simpleName}), original restaurado")
                } catch (restore: Exception) {
                    // El diario y la copia se conservan: se recuperará en el próximo arranque.
                    log("Editor: no se pudo restaurar ahora; copia guardada en ${backup.absolutePath}")
                    throw restore
                }
                throw e
            }
            journal.clear()
            keepBackup = false
            log("Editor: ${if (plan.inPlace) "etiqueta reescrita en su hueco" else "archivo reescrito"} (${newSize - originalSize} bytes de diferencia)")
            return newSize
        } finally {
            rewritten.delete()
            if (!keepBackup) backup.delete()
        }
    }

    /**
     * Cierra una reescritura interrumpida. [open] abre el destino apuntado (o null si ahora no se
     * puede: USB retirada, sin permiso), y entonces el diario se mantiene para otro intento.
     */
    fun recover(journal: Journal, open: (String) -> Channels?, log: (String) -> Unit = {}): Recovery {
        val entry = journal.read() ?: return Recovery.NONE
        val backup = File(entry.backupPath)
        val channels = try {
            open(entry.target)
        } catch (e: Exception) {
            null
        } ?: return Recovery.PENDING
        try {
            val size = channels.input.size()
            if (size == entry.newSize && crc(channels.input, size) == entry.newCrc) {
                finish(journal, backup)
                log("Editor: la reescritura interrumpida había terminado bien")
                return Recovery.COMPLETED
            }
            if (size == entry.originalSize && crc(channels.input, size) == entry.originalCrc) {
                finish(journal, backup)
                log("Editor: la reescritura interrumpida no había llegado a cambiar el archivo")
                return Recovery.RESTORED
            }
            if (!backup.exists() || backup.length() != entry.originalSize || crc(backup) != entry.originalCrc) {
                journal.clear()
                log("Editor: archivo a medio escribir y sin copia válida: ${entry.target}")
                return Recovery.LOST
            }
            restore(backup, channels.output, entry.originalSize)
            channels.sync()
            if (crc(channels.input, entry.originalSize) != entry.originalCrc) throw IOException("La restauración no se pudo verificar")
            finish(journal, backup)
            log("Editor: archivo a medio escribir restaurado desde la copia de seguridad")
            return Recovery.RESTORED
        } catch (e: Exception) {
            log("Editor: la recuperación falló (${e.javaClass.simpleName}); se reintentará")
            return Recovery.PENDING
        } finally {
            try {
                channels.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun finish(journal: Journal, backup: File) {
        journal.clear()
        backup.delete()
    }

    private fun restore(backup: File, output: FileChannel, originalSize: Long) {
        writeAll(backup, output)
        output.truncate(originalSize)
    }

    private fun writeAt(output: FileChannel, bytes: ByteArray, position: Long) {
        val buffer = ByteBuffer.wrap(bytes)
        var pos = position
        while (buffer.hasRemaining()) pos += output.write(buffer, pos)
    }

    private fun writeAll(source: File, output: FileChannel) {
        FileInputStream(source).use { input ->
            val buffer = ByteBuffer.allocate(64 * 1024)
            var position = 0L
            val channel = input.channel
            while (true) {
                buffer.clear()
                val n = channel.read(buffer)
                if (n < 0) break
                buffer.flip()
                while (buffer.hasRemaining()) position += output.write(buffer, position)
            }
        }
    }

    private fun copyRange(raf: RandomAccessFile, start: Long, end: Long, out: FileOutputStream) {
        val buffer = ByteArray(64 * 1024)
        var pos = start
        while (pos < end) {
            raf.seek(pos)
            val n = raf.read(buffer, 0, minOf(buffer.size.toLong(), end - pos).toInt())
            if (n <= 0) throw IOException("Lectura incompleta del audio")
            out.write(buffer, 0, n)
            pos += n
        }
    }

    private fun crc(file: File): Long = FileInputStream(file).use { crc(it.channel, file.length()) }

    /** CRC32 de los primeros [size] bytes con lecturas posicionales (no mueve el canal). */
    fun crc(channel: FileChannel, size: Long): Long {
        val crc = CRC32()
        val buffer = ByteBuffer.allocate(64 * 1024)
        var pos = 0L
        while (pos < size) {
            buffer.clear()
            if (size - pos < buffer.capacity()) buffer.limit((size - pos).toInt())
            val n = channel.read(buffer, pos)
            if (n <= 0) break
            crc.update(buffer.array(), 0, n)
            pos += n
        }
        return crc.value
    }
}
