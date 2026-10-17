package com.radillo.playerwave.data.tags

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.system.Os
import android.system.OsConstants
import androidx.core.content.ContextCompat
import com.radillo.playerwave.core.device.RealApiLevel
import com.radillo.playerwave.core.diagnostics.EventLog
import com.radillo.playerwave.core.model.Track
import com.radillo.playerwave.core.ui.CoverOverrides
import com.radillo.playerwave.data.library.db.RadilloDatabase
import com.radillo.playerwave.data.library.db.TrackEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Lo que el editor necesita saber antes de abrirse. [problem] != null: no se puede guardar. */
class EditorInfo(val problem: SaveOutcome.Reason?, val embeddedCover: ByteArray?) {
    val supported: Boolean get() = problem == null
}

/** Resultado de guardar. Los `Needs*` piden una acción al usuario y luego se reintenta. */
sealed interface SaveOutcome {
    class Saved(val track: Track) : SaveOutcome
    /** Android ≤ 9: falta el permiso de escritura en el almacenamiento. */
    data object NeedsWritePermission : SaveOutcome
    /** Android 10+: el sistema debe preguntar al usuario si deja modificar el archivo. */
    class NeedsConsent(val intentSender: IntentSender) : SaveOutcome
    /** USB o memoria sin acceso de escritura: el usuario debe conceder su carpeta una vez. */
    data object NeedsFolderAccess : SaveOutcome
    class Failed(val reason: Reason) : SaveOutcome

    enum class Reason { NOT_MP3, NOT_FOUND, NO_SPACE, INVALID_TAG, NO_ACCESS, IO, PENDING_RECOVERY }
}

/**
 * Escribe las etiquetas y la portada **dentro del MP3** de forma segura:
 *
 * 1. Consigue un descriptor de lectura/escritura del archivo, por la vía que el equipo permita:
 *    carpeta concedida (SAF, también para USB), MediaStore (con permiso en Android ≤ 9 o con el
 *    consentimiento del sistema en 10+) o la ruta directa.
 * 2. Comprueba que el descriptor es el archivo esperado (archivo normal del tamaño de MediaStore).
 * 3. [SafeRewrite]: copia de seguridad sincronizada y diario antes de tocar nada; si la etiqueta
 *    cabe, solo se reescribe su hueco (el audio no se mueve); verificación por CRC y restauración
 *    si algo falla. Misma ruta y mismo id de MediaStore: favoritos, listas y cola siguen funcionando.
 *    Si se corta la corriente a medias, [recoverPending] lo arregla en el siguiente arranque.
 * 4. Avisa a MediaStore, actualiza la caché de la biblioteca y la portada propia de la app.
 */
@Singleton
class TagWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: RadilloDatabase,
) {
    private val resolver: ContentResolver = context.contentResolver
    private val realApi: Int get() = RealApiLevel.effective

    /** Comprueba antes de editar que es MP3 de verdad y que su etiqueta se puede leer entera. */
    suspend fun inspect(track: Track): EditorInfo = withContext(Dispatchers.IO) {
        if (!Id3.isMp3(track.mimeType, track.fileName)) return@withContext EditorInfo(SaveOutcome.Reason.NOT_MP3, null)
        try {
            resolver.openFileDescriptor(Uri.parse(track.uri), "r")?.use { pfd ->
                val source = ChannelSource(FileInputStream(pfd.fileDescriptor).channel)
                val layout = Id3.read(source)
                if (!Id3.looksLikeMpeg(source, layout.audioStart, layout.audioEnd)) {
                    EditorInfo(SaveOutcome.Reason.NOT_MP3, null)
                } else {
                    EditorInfo(null, Id3.cover(layout.frames)?.takeIf { it.size <= MAX_COVER_BYTES })
                }
            } ?: EditorInfo(SaveOutcome.Reason.NOT_FOUND, null)
        } catch (e: InvalidTagException) {
            EditorInfo(SaveOutcome.Reason.INVALID_TAG, null)
        } catch (e: java.io.FileNotFoundException) {
            EditorInfo(SaveOutcome.Reason.NOT_FOUND, null)
        } catch (e: Exception) {
            // Sin lectura ahora (p. ej. permiso): se intentará al guardar.
            EditorInfo(null, null)
        }
    }

    /**
     * Imagen elegida por el usuario → JPEG de 600 px como máximo (≈ 50–120 KB): suficiente para la
     * pantalla del coche y para otros reproductores, sin engordar cada MP3.
     */
    suspend fun prepareCover(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= COVER_PX && bounds.outHeight / (sample * 2) >= COVER_PX) sample *= 2
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@withContext null
            val scale = minOf(1f, COVER_PX.toFloat() / maxOf(decoded.width, decoded.height))
            val bitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true)
                    .also { if (it !== decoded) decoded.recycle() }
            } else {
                decoded
            }
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                bitmap.recycle()
                out.toByteArray()
            }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** Una sola escritura a la vez en toda la app (los temporales y el diario son compartidos). */
    private val writeLock = Mutex()
    private val workDir: File get() = File(context.noBackupFilesDir, "tagwork")
    private val journal: Journal by lazy { Journal(File(context.noBackupFilesDir, "tag_journal.txt")) }

    /**
     * [consentGranted]: el usuario ya aceptó el diálogo del sistema (Android 10+).
     * [mayAskPermission]: false si el usuario ya negó el permiso de almacenamiento (se ofrece la
     * carpeta en su lugar).
     *
     * Una vez empezada, la escritura termina aunque se cierre la pantalla (no se cancela a medias).
     */
    suspend fun save(track: Track, edit: TagEdit, consentGranted: Boolean, mayAskPermission: Boolean = true): SaveOutcome =
        withContext(Dispatchers.IO + NonCancellable) {
            writeLock.withLock { saveLocked(track, edit, consentGranted, mayAskPermission) }
        }

    private suspend fun saveLocked(track: Track, edit: TagEdit, consentGranted: Boolean, mayAskPermission: Boolean): SaveOutcome {
        if (!Id3.isMp3(track.mimeType, track.fileName)) return SaveOutcome.Failed(SaveOutcome.Reason.NOT_MP3)
        // Antes de nada, cerrar cualquier reescritura interrumpida.
        if (recoverLocked() == SafeRewrite.Recovery.PENDING) return SaveOutcome.Failed(SaveOutcome.Reason.PENDING_RECOVERY)
        // El id de la caché debe seguir siendo esta canción en MediaStore (la base se puede regenerar).
        val row = mediaRow(track) ?: return SaveOutcome.Failed(SaveOutcome.Reason.NOT_FOUND)
        val access = openWritable(track, row, consentGranted, mayAskPermission)
        val granted = when (access) {
            is Access.Granted -> access
            is Access.Ask -> return access.outcome
        }
        return try {
            val newSize = rewriteInPlace(granted.pfd, granted.target, edit)
            SaveOutcome.Saved(afterWrite(track, row.path, edit, newSize))
        } catch (e: InvalidTagException) {
            EventLog.log("Editor: etiqueta ilegible en ${track.fileName}: ${e.message}")
            SaveOutcome.Failed(SaveOutcome.Reason.INVALID_TAG)
        } catch (e: NotMp3Exception) {
            EventLog.log("Editor: ${track.fileName} no contiene audio MP3")
            SaveOutcome.Failed(SaveOutcome.Reason.NOT_MP3)
        } catch (e: PendingRecoveryException) {
            SaveOutcome.Failed(SaveOutcome.Reason.PENDING_RECOVERY)
        } catch (e: NoSpaceException) {
            SaveOutcome.Failed(SaveOutcome.Reason.NO_SPACE)
        } catch (e: Exception) {
            EventLog.log("Editor: no se pudo guardar ${track.fileName}: ${e.javaClass.simpleName} ${e.message?.take(120)}")
            SaveOutcome.Failed(SaveOutcome.Reason.IO)
        } finally {
            try {
                granted.pfd.close()
            } catch (_: IOException) {
            }
        }
    }

    /** Al arrancar: termina o deshace una reescritura que se cortó (p. ej. al quitar la llave). */
    suspend fun recoverPending() = withContext(Dispatchers.IO + NonCancellable) {
        writeLock.withLock { recoverLocked() }
    }

    private fun recoverLocked(): SafeRewrite.Recovery = SafeRewrite.recover(journal, ::openTarget, EventLog::log)

    private fun openTarget(target: String): SafeRewrite.Channels? {
        val uri = Uri.parse(target)
        val pfd = try {
            if (uri.scheme == "file") {
                ParcelFileDescriptor.open(File(uri.path ?: return null), ParcelFileDescriptor.MODE_READ_WRITE)
            } else {
                resolver.openFileDescriptor(uri, "rw")
            }
        } catch (e: Exception) {
            null
        } ?: return null
        return SafeRewrite.Channels(
            input = FileInputStream(pfd.fileDescriptor).channel,
            output = FileOutputStream(pfd.fileDescriptor).channel,
            sync = { syncQuietly(pfd) },
            close = { pfd.close() },
        )
    }

    /** Fila actual de MediaStore para el id de la canción: ruta real y tamaño. */
    private class MediaRow(val path: String, val size: Long)

    private fun mediaRow(track: Track): MediaRow? = try {
        resolver.query(
            Uri.parse(track.uri),
            arrayOf(MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.SIZE),
            null, null, null,
        )?.use { c ->
            if (!c.moveToFirst()) return@use null
            val data = if (c.isNull(0)) null else c.getString(0)
            val path = data?.takeIf { it.isNotBlank() } ?: filePath(track)
            // Si la ruta no coincide con la de la biblioteca, el id ya es de otra canción.
            if (data != null && track.folderPath.isNotEmpty() && path.substringBeforeLast('/') != track.folderPath) return@use null
            MediaRow(path, c.getLong(1))
        }
    } catch (e: Exception) {
        null
    }

    // ---- Acceso de escritura ----

    private sealed interface Access {
        /** [target]: cómo volver a abrir el archivo para recuperarlo si se corta la escritura. */
        class Granted(val pfd: ParcelFileDescriptor, val target: String) : Access
        class Ask(val outcome: SaveOutcome) : Access
    }

    private fun openWritable(track: Track, row: MediaRow, consentGranted: Boolean, mayAskPermission: Boolean): Access {
        val path = row.path
        val expectedSize = File(path).takeIf { it.canRead() }?.length()?.takeIf { it > 0 } ?: row.size
        // 1. Una carpeta concedida por el usuario que contenga el archivo (funciona también en USB).
        val treeDoc = findInGrantedFolders(path, expectedSize)
        if (treeDoc != null) {
            openRw(treeDoc)?.let { pfd -> checked(pfd, expectedSize, treeDoc.toString())?.let { return it } }
        }
        // 2. A través de MediaStore.
        val mediaUri = Uri.parse(track.uri)
        try {
            resolver.openFileDescriptor(mediaUri, "rw")?.let { pfd ->
                checked(pfd, expectedSize, mediaUri.toString())?.let { return it }
            }
        } catch (e: SecurityException) {
            if (!consentGranted) {
                if (realApi >= 30 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val request = MediaStore.createWriteRequest(resolver, listOf(mediaUri))
                    return Access.Ask(SaveOutcome.NeedsConsent(request.intentSender))
                }
                if (realApi >= 29 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    return Access.Ask(SaveOutcome.NeedsConsent(e.userAction.actionIntent.intentSender))
                }
            }
            if (realApi <= 28 && !hasWritePermission() && mayAskPermission) return Access.Ask(SaveOutcome.NeedsWritePermission)
        } catch (e: Exception) {
            // FileNotFound/IO: p. ej. memoria extraíble sin escritura por esta vía.
        }
        // 3. Ruta directa (Android ≤ 9 con permiso; en USB suele estar prohibido).
        if (realApi <= 28 && hasWritePermission()) {
            val file = File(path)
            if (file.exists()) {
                try {
                    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE)
                    checked(pfd, expectedSize, Uri.fromFile(file).toString())?.let { return it }
                } catch (_: Exception) {
                }
            }
        }
        // 4. Pedir la carpeta (si el equipo tiene selector de carpetas y aún no se concedió).
        if (treeDoc == null && canPickFolders() && volumeOf(path) != null) return Access.Ask(SaveOutcome.NeedsFolderAccess)
        return Access.Ask(SaveOutcome.Failed(SaveOutcome.Reason.NO_ACCESS))
    }

    /** Solo se acepta un descriptor de archivo normal con el tamaño esperado; si no, se cierra. */
    private fun checked(pfd: ParcelFileDescriptor, expectedSize: Long, target: String): Access.Granted? {
        val ok = try {
            val st = Os.fstat(pfd.fileDescriptor)
            OsConstants.S_ISREG(st.st_mode) && (expectedSize <= 0 || st.st_size == expectedSize)
        } catch (e: Exception) {
            false
        }
        if (ok) return Access.Granted(pfd, target)
        try {
            pfd.close()
        } catch (_: IOException) {
        }
        EventLog.log("Editor: descriptor descartado (no es el archivo esperado)")
        return null
    }

    private fun openRw(uri: Uri): ParcelFileDescriptor? = try {
        resolver.openFileDescriptor(uri, "rw")
    } catch (e: Exception) {
        null
    }

    private fun hasWritePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun canPickFolders(): Boolean =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).resolveActivity(context.packageManager) != null

    /** Guarda el permiso duradero sobre la carpeta elegida por el usuario. */
    fun rememberFolder(treeUri: Uri) {
        try {
            resolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            EventLog.log("Editor: carpeta concedida ${DocumentsContract.getTreeDocumentId(treeUri)}")
        } catch (e: Exception) {
            EventLog.log("Editor: no se pudo guardar el permiso de la carpeta: ${e.javaClass.simpleName}")
        }
    }

    /**
     * Busca el archivo dentro de las carpetas concedidas **del mismo volumen** que la canción (si la
     * misma carpeta existe en la memoria interna y en la USB, nunca se toca la copia equivocada).
     * Los id de documento son «volumen:ruta/relativa» y la ruta puede tener cualquier punto de
     * montaje, así que se prueban sus sufijos del más largo al más corto, y solo se acepta un
     * documento con el mismo nombre, el mismo tamaño y la misma fecha de modificación (si se conoce).
     */
    private fun findInGrantedFolders(path: String, expectedSize: Long): Uri? {
        if (expectedSize <= 0) return null
        val volume = volumeOf(path) ?: return null
        val segments = path.trim('/').split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null
        val lastModified = File(path).takeIf { it.canRead() }?.lastModified() ?: 0L
        for (permission in resolver.persistedUriPermissions) {
            if (!permission.isWritePermission) continue
            val tree = permission.uri
            val treeDocId = try {
                DocumentsContract.getTreeDocumentId(tree)
            } catch (e: Exception) {
                continue
            }
            if (!treeDocId.substringBefore(':', "").equals(volume, ignoreCase = true)) continue
            val treePrefix = if (treeDocId.endsWith(":")) treeDocId else "$treeDocId/"
            for (start in segments.indices) {
                val docId = treeDocId.substringBefore(':') + ":" + segments.drop(start).joinToString("/")
                if (!docId.startsWith(treePrefix)) continue
                val docUri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
                if (matches(docUri, segments.last(), expectedSize, lastModified)) return docUri
            }
        }
        return null
    }

    /** «primary» para la memoria interna o el UUID de la USB/tarjeta (como en los id de documento). */
    private fun volumeOf(path: String): String? = try {
        val sm = context.getSystemService(StorageManager::class.java)
        val v = sm?.getStorageVolume(File(path))
        when {
            v == null -> null
            v.isPrimary -> "primary"
            else -> v.uuid
        }
    } catch (e: Exception) {
        null
    }

    private fun matches(docUri: Uri, name: String, size: Long, lastModified: Long): Boolean = try {
        resolver.query(
            docUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED),
            null, null, null,
        )?.use { c ->
            c.moveToFirst() && c.getString(0) == name && c.getLong(1) == size &&
                (lastModified <= 0 || c.isNull(2) || kotlin.math.abs(c.getLong(2) - lastModified) <= 2_000)
        } ?: false
    } catch (e: Exception) {
        false
    }

    private fun filePath(track: Track): String =
        if (track.folderPath.isEmpty()) "/" + track.fileName else track.folderPath + "/" + track.fileName

    // ---- Reescritura segura ----

    private class NoSpaceException : IOException()

    /** Devuelve el tamaño nuevo del archivo. */
    private fun rewriteInPlace(pfd: ParcelFileDescriptor, target: String, edit: TagEdit): Long {
        val input = FileInputStream(pfd.fileDescriptor).channel // no se cierran: el descriptor es de pfd
        val output = FileOutputStream(pfd.fileDescriptor).channel
        val dir = workDir.apply { mkdirs() }
        if (dir.usableSpace < input.size() * 2 + SPACE_MARGIN) throw NoSpaceException()
        return SafeRewrite.run(
            input = input,
            output = output,
            workDir = dir,
            journal = journal,
            target = target,
            edit = edit,
            sync = { syncQuietly(pfd) },
            log = EventLog::log,
        )
    }

    private fun syncQuietly(pfd: ParcelFileDescriptor) {
        try {
            pfd.fileDescriptor.sync()
        } catch (_: Exception) {
        }
    }

    // ---- Después de escribir ----

    private suspend fun afterWrite(track: Track, path: String, edit: TagEdit, newSize: Long): Track {
        when (val cover = edit.cover) {
            is CoverChange.Replace -> CoverOverrides.set(track.id, cover.bytes)
            CoverChange.Remove -> CoverOverrides.setNone(track.id)
            CoverChange.Keep -> Unit
        }
        val updated = track.copy(
            title = edit.title.trim(),
            artist = edit.artist.trim(),
            album = edit.album.trim(),
            year = edit.year ?: 0,
            trackNumber = edit.trackNumber ?: 0,
            sizeBytes = newSize,
        )
        // La biblioteca se actualiza al momento; MediaStore lo confirmará al reescanear el archivo.
        database.trackDao().upsertAll(listOf(TrackEntity.fromTrack(updated)))
        MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf("audio/mpeg"), null)
        EventLog.log("Editor: etiquetas guardadas en ${track.fileName}")
        return updated
    }

    private companion object {
        const val COVER_PX = 600
        const val MAX_COVER_BYTES = 8 * 1024 * 1024
        const val SPACE_MARGIN = 4L * 1024 * 1024
    }
}
