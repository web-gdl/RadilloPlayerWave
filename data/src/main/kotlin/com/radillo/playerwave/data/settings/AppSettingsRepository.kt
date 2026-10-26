package com.radillo.playerwave.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.radillo.playerwave.core.model.AppSettings
import com.radillo.playerwave.core.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface AppSettingsRepository {
    val settings: Flow<AppSettings>

    /** Valor actual, síncrono (lo usa la actividad para aplicar el tema antes del primer frame). */
    fun snapshot(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

/** Ajustes generales en `SharedPreferences` (archivo "app_settings"). */
@Singleton
class PrefsAppSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) : AppSettingsRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val lock = Any()

    override val settings: Flow<AppSettings> = prefs.observe { it.toAppSettings() }
    override fun snapshot(): AppSettings = prefs.toAppSettings()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        synchronized(lock) {
            val next = transform(prefs.toAppSettings())
            prefs.edit()
                .putString(KEY_THEME, next.themeMode.name)
                .putBoolean(KEY_ARTWORK_COLORS, next.artworkColors)
                // -1 = según el dispositivo, 0 = libre, 1 = horizontal fija.
                .putInt(KEY_LOCK_LANDSCAPE, when (next.lockLandscape) { null -> -1; false -> 0; true -> 1 })
                .apply()
        }
    }

    private fun SharedPreferences.toAppSettings(): AppSettings {
        val d = AppSettings()
        val mode = getString(KEY_THEME, null)?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: d.themeMode
        return AppSettings(
            themeMode = mode,
            artworkColors = getBoolean(KEY_ARTWORK_COLORS, d.artworkColors),
            lockLandscape = when (getInt(KEY_LOCK_LANDSCAPE, -1)) { 0 -> false; 1 -> true; else -> null },
        )
    }

    companion object {
        private const val FILE = "app_settings"

        /**
         * Lee el modo de tema sin pasar por la inyección (la actividad lo necesita en
         * `attachBaseContext`, antes de que Hilt inyecte nada).
         */
        fun readThemeMode(context: Context): ThemeMode {
            val name = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_THEME, null)
            return ThemeMode.entries.firstOrNull { it.name == name } ?: ThemeMode.SYSTEM
        }

        private const val KEY_THEME = "theme_mode"
        private const val KEY_ARTWORK_COLORS = "artwork_colors"
        private const val KEY_LOCK_LANDSCAPE = "lock_landscape"
    }
}

