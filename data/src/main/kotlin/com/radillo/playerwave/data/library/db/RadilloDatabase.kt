package com.radillo.playerwave.data.library.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Versión 2 (Fase 3): favoritos y listas de reproducción.
 * La versión 1 solo contenía la caché de MediaStore, por lo que se usa migración destructiva;
 * a partir de ahora cualquier cambio de esquema debe llevar una migración explícita para no
 * perder los datos del usuario.
 */
@Database(
    entities = [TrackEntity::class, FavoriteEntity::class, PlaylistEntity::class, PlaylistTrackEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class RadilloDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        const val NAME = "radillo.db"
    }
}
