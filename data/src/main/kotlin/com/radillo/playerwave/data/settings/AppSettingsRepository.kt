package com.radillo.playerwave.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.radillo.playerwave.core.model.AppSettings
import com.radillo.playerwave.core.model.AudioEffectsSettings
import com.radillo.playerwave.core.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

interface AppSettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

interface AudioEffectsSettingsRepository {
    val settings: Flow<AudioEffectsSettings>
    suspend fun update(transform: (AudioEffectsSettings) -> AudioEffectsSettings)
}

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

@Singleton
class DataStoreAppSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) : AppSettingsRepository {

    private val store = context.appDataStore

    private val data: Flow<Preferences> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    override val settings: Flow<AppSettings> = data.map { it.toAppSettings() }

    /** Ajustes de efectos de audio (misma preferencia, claves distintas). */
    val effects: Flow<AudioEffectsSettings> = data.map { it.toEffects() }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val next = transform(prefs.toAppSettings())
            prefs[KEY_THEME] = next.themeMode.name
            prefs[KEY_ARTWORK_COLORS] = next.artworkColors
        }
    }

    suspend fun updateEffects(transform: (AudioEffectsSettings) -> AudioEffectsSettings) {
        store.edit { prefs ->
            val next = transform(prefs.toEffects())
            prefs[KEY_EQ_ENABLED] = next.enabled
            prefs[KEY_EQ_BANDS] = next.bandLevelsMb.joinToString(",")
            prefs[KEY_EQ_PRESET] = next.presetIndex
            prefs[KEY_BASS_BOOST] = next.bassBoostStrength
        }
    }

    private fun Preferences.toAppSettings(): AppSettings {
        val defaults = AppSettings()
        val mode = this[KEY_THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: defaults.themeMode
        return AppSettings(
            themeMode = mode,
            artworkColors = this[KEY_ARTWORK_COLORS] ?: defaults.artworkColors,
        )
    }

    private fun Preferences.toEffects(): AudioEffectsSettings {
        val defaults = AudioEffectsSettings()
        val bands = this[KEY_EQ_BANDS]?.takeIf { it.isNotBlank() }
            ?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: defaults.bandLevelsMb
        return AudioEffectsSettings(
            enabled = this[KEY_EQ_ENABLED] ?: defaults.enabled,
            bandLevelsMb = bands,
            presetIndex = this[KEY_EQ_PRESET] ?: defaults.presetIndex,
            bassBoostStrength = this[KEY_BASS_BOOST] ?: defaults.bassBoostStrength,
        )
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_ARTWORK_COLORS = booleanPreferencesKey("artwork_colors")
        val KEY_EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val KEY_EQ_BANDS = stringPreferencesKey("eq_bands_mb")
        val KEY_EQ_PRESET = intPreferencesKey("eq_preset")
        val KEY_BASS_BOOST = intPreferencesKey("bass_boost")
    }
}

/** Adaptador para exponer la parte de efectos como su propia interfaz. */
@Singleton
class AudioEffectsSettingsAdapter @Inject constructor(
    private val delegate: DataStoreAppSettingsRepository,
) : AudioEffectsSettingsRepository {
    override val settings: Flow<AudioEffectsSettings> get() = delegate.effects
    override suspend fun update(transform: (AudioEffectsSettings) -> AudioEffectsSettings) = delegate.updateEffects(transform)
}
