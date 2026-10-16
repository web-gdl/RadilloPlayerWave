package com.radillo.playerwave.data.library.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TrackEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class RadilloDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao

    companion object {
        const val NAME = "radillo.db"
    }
}
