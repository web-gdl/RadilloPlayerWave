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
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.radillo.playerwave.core.device.RealApiLevel
import com.radillo.playerwave.core.diagnostics.EventLog
import com.radillo.playerwave.core.model.Track
import com.radillo.playerwave.core.ui.CoverOverrides
import com.radillo.playerwave.data.library.db.RadilloDatabase
import com.radillo.playerwave.data.library.db.TrackEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Lo que el editor necesita saber antes de abrirse. */
class EditorInfo(val supported: Boolean, val embeddedCover: ByteArray?)

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

    enum class Reason { NOT_MP3, NOT_FOUND, NO_SPACE, INVALID_TAG, NO_ACCESS, IO }
}

/**
 * Escribe las etiquetas y la portada **dentro del MP3** de forma segura:
 *
 * 1. Consigue un descriptor de lectura/escritura del archivo, por la vía que el equipo permita:
 *    carpeta concedida (SAF, también para USB), MediaStore (con permiso en Android ≤ 9 o con el
 *    consentimiento del sistema en 10+) o la ruta directa.
 * 2. Copia el original a la caché (copia de seguridad) y genera ahí el archivo nuevo con [Id3]:
 *    el audio se copia byte a byte.
 * 3. Reescribe el archivo **en su sitio** (misma ruta y mismo id de MediaStore: favoritos, listas
 *    y cola siguen funcionando), sincroniza a disco y **verifica** lo escrito. Si algo falla,
 *    restaura la copia de seguridad: el archivo queda como estaba.
 * 4. Avisa a MediaStore, actualiza la caché de la biblioteca y la portada propia de la app.
 */
@Singleton
class TagWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: RadilloDatabase,
) {
    private val resolver: ContentResolver = context.contentResolver
    private val realApi: Int get() = RealApiLevel.effective

    suspend fun inspect(track: Track): EditorInfo = withContext(Dispatchers.IO) {
        val supported = Id3.isMp3(track.mimeType, track.fileName)
        val cover = if (!supported) null else try {
            resolver.openFileDescriptor(Uri.parse(track.uri), "r")?.use { pfd ->
                val frames = Id3.read(ChannelSource(FileInputStream(pfd.fileDescriptor).channel)).frames
                Id3.cover(frames)?.takeIf { it.size <= MAX_COVER_BYTES }
            }
        } catch (e: Exception) {
            null
        }
        EditorInfo(supported, cover)
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

    /** [consentGranted]: el usuario ya aceptó el diálogo del sistema (Android 10+). */
    suspend fun save(track: Track, edit: TagEdit, consentGranted: Boolean): SaveOutcome = withContext(Dispatchers.IO) {
        if (!Id3.isMp3(track.mimeType, track.fileName)) return@withContext SaveOutcome.Failed(SaveOutcome.Reason.NOT_MP3)
        val target = when (val access = openWritable(track, consentGranted)) {
            is Access.Granted -> access.pfd
            is Access.Ask -> return@withContext access.outcome
        }
        try {
            val newSize = rewriteInPlace(target, edit)
            SaveOutcome.Saved(afterWrite(track, edit, newSize))
        } catch (e: InvalidTagException) {
            EventLog.log("Editor: etiqueta dañada en ${track.fileName}: ${e.message}")
            SaveOutcome.Failed(SaveOutcome.Reason.INVALID_TAG)
        } catch (e: NoSpaceException) {
            SaveOutcome.Failed(SaveOutcome.Reason.NO_SPACE)
        } catch (e: Exception) {
            EventLog.log("Editor: no se pudo guardar ${track.fileName}: ${e.javaClass.simpleName} ${e.message?.take(120)}")
            SaveOutcome.Failed(SaveOutcome.Reason.IO)
        } finally {
            try {
                target.close()
            } catch (_: IOException) {
            }
        }
    }

    // ---- Acceso de escritura ----

    private sealed interface Access {
        class Granted(val pfd: ParcelFileDescriptor) : Access
        class Ask(val outcome: SaveOutcome) : Access
    }

    private fun openWritable(track: Track, consentGranted: Boolean): Access {
        val path = filePath(track)
        // 1. Una carpeta concedida por el usuario que contenga el archivo (funciona también en USB).
        val treeDoc = findInGrantedFolders(path, track)
        if (treeDoc != null) {
            openRw(treeDoc)?.let { return Access.Granted(it) }
        }
        // 2. A través de MediaStore.
        val mediaUri = Uri.parse(track.uri)
        try {
            resolver.openFileDescriptor(mediaUri, "rw")?.let { return Access.Granted(it) }
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
            if (realApi <= 28 && !hasWritePermission()) return Access.Ask(SaveOutcome.NeedsWritePermission)
        } catch (e: Exception) {
            // FileNotFound/IO: p. ej. memoria extraíble sin escritura por esta vía.
        }
        // 3. Ruta directa (Android ≤ 9 con permiso; en USB suele estar prohibido).
        if (realApi <= 28 && hasWritePermission()) {
            val file = File(path)
            if (!file.exists()) return Access.Ask(SaveOutcome.Failed(SaveOutcome.Reason.NOT_FOUND))
            try {
                return Access.Granted(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE))
            } catch (_: Exception) {
            }
        }
        // 4. Pedir la carpeta (si el equipo tiene selector de carpetas y aún no se concedió).
        if (treeDoc == null && canPickFolders()) return Access.Ask(SaveOutcome.NeedsFolderAccess)
        return Access.Ask(SaveOutcome.Failed(SaveOutcome.Reason.NO_ACCESS))
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
     * Busca el archivo dentro de las carpetas concedidas. Los id de documento del almacenamiento
     * son «volumen:ruta/relativa», y la ruta del archivo puede tener cualquier punto de montaje
     * (`/storage/1234-ABCD/…`, `/mnt/usb_storage/…`): se prueban sus sufijos, del más largo al más
     * corto, y solo se acepta un documento con **el mismo nombre y el mismo tamaño**, para no tocar
     * nunca otro archivo que se llame igual.
     */
    private fun findInGrantedFolders(path: String, track: Track): Uri? {
        val segments = path.trim('/').split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null
        val expectedSize = File(path).takeIf { it.exists() }?.length() ?: track.sizeBytes
        for (permission in resolver.persistedUriPermissions) {
            if (!permission.isWritePermission) continue
            val tree = permission.uri
            val treeDocId = try {
                DocumentsContract.getTreeDocumentId(tree)
            } catch (e: Exception) {
                continue
            }
            val volume = treeDocId.substringBefore(':', "")
            if (volume.isEmpty()) continue
            val treePrefix = if (treeDocId.endsWith(":")) treeDocId else "$treeDocId/"
            for (start in segments.indices) {
                val docId = "$volume:" + segments.drop(start).joinToString("/")
                if (!docId.startsWith(treePrefix)) continue
                val docUri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
                if (matches(docUri, segments.last(), expectedSize)) return docUri
            }
        }
        return null
    }

    private fun matches(docUri: Uri, name: String, size: Long): Boolean = try {
        resolver.query(docUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { c ->
            c.moveToFirst() && c.getString(0) == name && (size <= 0 || c.getLong(1) == size)
        } ?: false
    } catch (e: Exception) {
        false
    }

    private fun filePath(track: Track): String =
        if (track.folderPath.isEmpty()) "/" + track.fileName else track.folderPath + "/" + track.fileName

    // ---- Reescritura segura ----

    private class NoSpaceException : IOException()

    /** Devuelve el tamaño nuevo del archivo. */
    private fun rewriteInPlace(pfd: ParcelFileDescriptor, edit: TagEdit): Long {
        val input = FileInputStream(pfd.fileDescriptor).channel // no se cierran: el descriptor es de pfd
        val output = FileOutputStream(pfd.fileDescriptor).channel
        val cache = context.cacheDir
        if (cache.usableSpace < input.size() * 2 + SPACE_MARGIN) throw NoSpaceException()
        return SafeRewrite.run(
            input = input,
            output = output,
            workDir = cache,
            rescueDir = context.getExternalFilesDir(null) ?: cache,
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

    private suspend fun afterWrite(track: Track, edit: TagEdit, newSize: Long): Track {
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
        MediaScannerConnection.scanFile(context, arrayOf(filePath(track)), arrayOf("audio/mpeg"), null)
        EventLog.log("Editor: etiquetas guardadas en ${track.fileName}")
        return updated
    }

    private companion object {
        const val COVER_PX = 600
        const val MAX_COVER_BYTES = 8 * 1024 * 1024
        const val SPACE_MARGIN = 4L * 1024 * 1024
    }
}
