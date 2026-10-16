package com.radillo.playerwave.data.library

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Estado del último escaneo exitoso de música. */
data class ScanCache(
    val timestampMs: Long = 0L,  // cuándo se hizo el escaneo
    val musicFileCount: Int = 0,  // cuántos archivos encontró (como validación)
)

interface ScanCacheRepository {
    fun getScanCache(): ScanCache
    fun saveScanCache(cache: ScanCache)
    fun clearCache()

    /** ¿Es válido el cache? (menos de 1 hora desde el último escaneo) */
    fun isCacheValid(): Boolean
}

/** Persiste el timestamp del último escaneo para evitar rescans frecuentes. */
@Singleton
class SharedScanCacheRepository @Inject constructor(
    @ApplicationContext context: Context,
) : ScanCacheRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun getScanCache(): ScanCache {
        val timestampMs = prefs.getLong(KEY_TIMESTAMP, 0L)
        val count = prefs.getInt(KEY_COUNT, 0)
        return ScanCache(timestampMs, count)
    }

    override fun saveScanCache(cache: ScanCache) {
        prefs.edit()
            .putLong(KEY_TIMESTAMP, cache.timestampMs)
            .putInt(KEY_COUNT, cache.musicFileCount)
            .apply()
    }

    override fun clearCache() {
        prefs.edit()
            .remove(KEY_TIMESTAMP)
            .remove(KEY_COUNT)
            .apply()
    }

    override fun isCacheValid(): Boolean {
        val cache = getScanCache()
        if (cache.timestampMs == 0L) return false  // nunca se ha escaneado

        val ageMs = System.currentTimeMillis() - cache.timestampMs
        val ONE_HOUR_MS = 60 * 60 * 1000L
        return ageMs < ONE_HOUR_MS
    }

    companion object {
        private const val FILE = "scan_cache"
        private const val KEY_TIMESTAMP = "last_scan_timestamp_ms"
        private const val KEY_COUNT = "music_file_count"
    }
}
