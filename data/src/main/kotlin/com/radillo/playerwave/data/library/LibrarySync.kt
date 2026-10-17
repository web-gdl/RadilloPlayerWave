package com.radillo.playerwave.data.library

import com.radillo.playerwave.data.library.db.TrackEntity

/**
 * Huella barata de la música en MediaStore: número de pistas, id más alto, fecha de modificación
 * más reciente y tamaño total. Se calcula leyendo solo tres columnas (milisegundos incluso con
 * miles de canciones) y basta para saber si hace falta volver a escanear.
 */
data class LibrarySignature(
    val count: Int,
    val maxId: Long,
    val maxModifiedSec: Long,
    val totalBytes: Long,
) {
    fun encode(): String = "$count:$maxId:$maxModifiedSec:$totalBytes"

    companion object {
        fun decode(value: String?): LibrarySignature? {
            val parts = value?.split(':') ?: return null
            if (parts.size != 4) return null
            return LibrarySignature(
                count = parts[0].toIntOrNull() ?: return null,
                maxId = parts[1].toLongOrNull() ?: return null,
                maxModifiedSec = parts[2].toLongOrNull() ?: return null,
                totalBytes = parts[3].toLongOrNull() ?: return null,
            )
        }
    }
}

/** Resultado de una sincronización con MediaStore (para mostrar «12 canciones nuevas»). */
data class ScanResult(
    val added: Int,
    val removed: Int,
    val updated: Int,
    val total: Int,
    /** `true` si no se escaneó porque la huella no había cambiado. */
    val skipped: Boolean = false,
) {
    val changed: Boolean get() = added + removed + updated > 0

    companion object {
        fun unchanged(total: Int) = ScanResult(0, 0, 0, total, skipped = true)
    }
}

/**
 * Diferencia entre la caché (Room) y lo leído de MediaStore. Solo se escriben las filas nuevas o
 * modificadas y se borran las desaparecidas: si nada cambió, Room no invalida ninguna consulta y la
 * lista en pantalla no parpadea ni se recalcula.
 */
data class LibraryDiff(
    val upserts: List<TrackEntity>,
    val deletes: List<Long>,
    val added: Int,
) {
    val updated: Int get() = upserts.size - added

    companion object {
        fun compute(existing: List<TrackEntity>, scanned: List<TrackEntity>): LibraryDiff {
            val old = existing.associateBy { it.id }
            val seen = HashSet<Long>(scanned.size * 2)
            val upserts = ArrayList<TrackEntity>()
            var added = 0
            for (entity in scanned) {
                seen += entity.id
                val previous = old[entity.id]
                if (previous == null) {
                    added++
                    upserts += entity
                } else if (previous != entity) {
                    upserts += entity
                }
            }
            val deletes = existing.asSequence().map { it.id }.filterNot { it in seen }.toList()
            return LibraryDiff(upserts, deletes, added)
        }
    }
}
