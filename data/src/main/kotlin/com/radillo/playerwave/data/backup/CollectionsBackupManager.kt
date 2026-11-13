package com.radillo.playerwave.data.backup

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.radillo.playerwave.core.diagnostics.EventLog
import com.radillo.playerwave.data.library.db.RadilloDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Respaldo de favoritos y listas que sobrevive a desinstalar la app.
 *
 * - Cada cambio se guarda (a los 2 s) en `Documentos/RadilloPlayerWave/listas-y-favoritos.json`,
 *   fuera de los datos privados de la app.
 * - Al instalar de nuevo, si la app no tiene favoritos ni listas y ese archivo existe, se
 *   recuperan solos. Lo que aún no aparece (canciones de una USB no puesta) queda pendiente en el
 *   mismo archivo y se agrega en cuanto aparece.
 * - En Android 11 o más nuevo el sistema no deja leer el archivo de una instalación anterior: ahí se
 *   usa «Importar» en Ajustes eligiendo el archivo. «Exportar» guarda una copia donde se quiera.
 */
@Singleton
class CollectionsBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    database: RadilloDatabase,
) {
    private val restorer = CollectionsRestorer(database)
    private val playlistDao = database.playlistDao()
    private val trackDao = database.trackDao()
    private val mutex = Mutex()

    /** Lo último que hay en el archivo (para no reescribirlo igual y describir canciones ausentes). */
    private var lastFile: BackupFile? = null
    private var pending = CollectionsSnapshot.EMPTY
    /** La restauración inicial crea también las listas vacías; las siguientes, solo si encuentran algo. */
    private var initialRestore = false
    private var sawData = false

    val backupFile: File
        get() = File(
            @Suppress("DEPRECATION") Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "RadilloPlayerWave/$FILE_NAME",
        )

    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            // Hasta que la biblioteca tiene canciones (permiso concedido y primer escaneo hecho) no se
            // decide si hay que recuperar: antes no se podría leer el respaldo ni encontrar las canciones.
            launch {
                trackDao.observeSignature().distinctUntilChanged().collect { signature ->
                    if (signature.startsWith("0:")) return@collect
                    runCatching {
                        if (!prepared) prepare()
                        resolvePending()
                    }.onFailure { EventLog.log("Respaldo de listas: error al recuperar (${it.message})") }
                }
            }
            playlistDao.observeCollectionsSignature().distinctUntilChanged().debounce(2_000).collect {
                runCatching { writeAuto() }.onFailure { EventLog.log("Respaldo de listas: no se pudo guardar (${it.message})") }
            }
        }
    }

    @Volatile private var prepared = false

    private suspend fun prepare() = mutex.withLock {
        if (prepared) return@withLock
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = runCatching { readStored() }.getOrNull()
        lastFile = stored
        val firstRun = !prefs.getBoolean(KEY_CHECKED, false)
        pending = if (firstRun && stored != null && playlistDao.collectionsCount() == 0) {
            initialRestore = true
            EventLog.log("Respaldo de listas encontrado: se recupera (${stored.everything.trackCount} canciones)")
            stored.everything
        } else {
            stored?.pending ?: CollectionsSnapshot.EMPTY
        }
        prefs.edit().putBoolean(KEY_CHECKED, true).apply()
        prepared = true
    }

    private suspend fun resolvePending() = mutex.withLock {
        if (pending.isEmpty) return@withLock
        val result = restorer.apply(pending, createEmpty = initialRestore)
        initialRestore = false
        pending = result.pending
        if (result.favoritesAdded + result.playlistsCreated + result.tracksAdded > 0) {
            EventLog.log(
                "Respaldo de listas: +${result.favoritesAdded} favoritos, +${result.playlistsCreated} listas, " +
                    "+${result.tracksAdded} canciones en listas; pendientes ${result.pending.trackCount}",
            )
        }
    }

    private suspend fun currentFile(): BackupFile {
        pending = restorer.prunePending(pending)
        return BackupFile(restorer.snapshot(lastFile?.everything ?: CollectionsSnapshot.EMPTY), pending)
    }

    private suspend fun writeAuto() = mutex.withLock {
        if (!prepared) return@withLock
        val file = currentFile()
        if (!file.saved.isEmpty) sawData = true
        if (file == lastFile) return@withLock
        // Nunca se cambia un respaldo con datos por uno vacío, salvo que el usuario haya borrado lo que tenía.
        if (file.isEmpty && lastFile?.isEmpty == false && !sawData) return@withLock
        if (!canWrite()) return@withLock
        val target = backupFile
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "$FILE_NAME.tmp")
        tmp.writeText(BackupCodec.encode(file, System.currentTimeMillis()))
        if (!tmp.renameTo(target)) {
            target.delete()
            check(tmp.renameTo(target)) { "no se pudo reemplazar ${target.path}" }
        }
        lastFile = file
    }

    /** Copia del respaldo actual donde elija el usuario (Ajustes ▸ Exportar). */
    suspend fun exportTo(out: OutputStream): CollectionsSnapshot = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = currentFile()
            out.bufferedWriter().use { it.write(BackupCodec.encode(file, System.currentTimeMillis())) }
            file.saved
        }
    }

    /** Agrega a la app un respaldo elegido por el usuario; `null` si el archivo no es un respaldo. */
    suspend fun importFrom(input: InputStream): RestoreResult? = withContext(Dispatchers.IO) {
        val decoded = BackupCodec.decode(input.bufferedReader().use { it.readText() }) ?: return@withContext null
        mutex.withLock {
            val result = restorer.apply(decoded.everything, createEmpty = true)
            pending = pending + result.pending
            EventLog.log("Respaldo de listas importado: +${result.favoritesAdded} favoritos, +${result.playlistsCreated} listas")
            result
        }
    }

    private fun readStored(): BackupFile? {
        val file = backupFile
        if (!file.isFile || !file.canRead()) return null
        return BackupCodec.decode(file.readText())
    }

    /** En Android 9 o anterior escribir en Documentos necesita el permiso de almacenamiento. */
    private fun canWrite(): Boolean =
        Build.VERSION.SDK_INT > Build.VERSION_CODES.P ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val FILE_NAME = "listas-y-favoritos.json"
        private const val PREFS = "collections_backup"
        private const val KEY_CHECKED = "restore_checked"
    }
}
