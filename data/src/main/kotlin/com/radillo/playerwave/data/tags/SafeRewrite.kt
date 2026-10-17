package com.radillo.playerwave.data.tags

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Reescritura de un MP3 **en su sitio** con copia de seguridad, verificación y restauración.
 * Kotlin puro sobre canales de archivo, para probarla sin dispositivo.
 *
 * Orden: copia de seguridad → archivo nuevo generado desde la copia (si la etiqueta está dañada se
 * aborta aquí, sin haber tocado nada) → escritura sobre el original → recorte → sincronización →
 * verificación. Si la escritura o la verificación fallan, se vuelve a escribir la copia; si eso
 * también falla, la copia se conserva en [rescueDir] para poder recuperarla.
 */
object SafeRewrite {

    /** Devuelve el tamaño nuevo del archivo. */
    fun run(
        input: FileChannel,
        output: FileChannel,
        workDir: File,
        rescueDir: File,
        edit: TagEdit,
        sync: () -> Unit = {},
        log: (String) -> Unit = {},
        /** Solo pruebas: se llama tras escribir el contenido nuevo (puede lanzar para simular un fallo). */
        afterWrite: () -> Unit = {},
    ): Long {
        val originalSize = input.size()
        if (originalSize <= 0) throw IOException("Archivo vacío o no accesible")
        val backup = File(workDir, "tag_backup.mp3")
        val rewritten = File(workDir, "tag_new.mp3")
        var keepBackup = false
        try {
            FileOutputStream(backup).use { out ->
                var pos = 0L
                while (pos < originalSize) {
                    val n = input.transferTo(pos, originalSize - pos, out.channel)
                    if (n <= 0) throw IOException("Copia de seguridad incompleta")
                    pos += n
                }
            }
            if (backup.length() != originalSize) throw IOException("Copia de seguridad incompleta")
            val audioBytes = RandomAccessFile(backup, "r").use { raf ->
                FileOutputStream(rewritten).use { out -> Id3.rewrite(FileSource(raf), edit, out) }
            }
            val newSize = rewritten.length()
            try {
                writeAll(rewritten, output)
                afterWrite()
                output.truncate(newSize)
                sync()
                verify(input, newSize, audioBytes, edit)
            } catch (e: Exception) {
                try {
                    writeAll(backup, output)
                    output.truncate(originalSize)
                    sync()
                    log("Editor: escritura fallida (${e.javaClass.simpleName}), original restaurado")
                } catch (restore: Exception) {
                    keepBackup = true
                    rescueDir.mkdirs()
                    val rescue = File(rescueDir, "rescate_${System.currentTimeMillis()}.mp3")
                    if (!backup.renameTo(rescue)) backup.copyTo(rescue, overwrite = true)
                    log("Editor: ¡no se pudo restaurar el original! Copia en ${rescue.absolutePath}")
                    throw restore
                }
                throw e
            }
            return newSize
        } finally {
            rewritten.delete()
            if (!keepBackup) backup.delete()
        }
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

    /** Relee lo escrito: tamaño, título nuevo legible y la misma cantidad de audio. */
    private fun verify(input: FileChannel, expectedSize: Long, audioBytes: Long, edit: TagEdit) {
        if (input.size() != expectedSize) throw IOException("Tamaño inesperado tras escribir")
        val layout = Id3.read(ChannelSource(input))
        if (Id3.text(layout.frames, "TIT2") != edit.title.trim()) throw IOException("La etiqueta no se leyó como se escribió")
        if (layout.audioEnd - layout.audioStart != audioBytes) throw IOException("El audio no coincide tras escribir")
    }
}
