package com.radillo.playerwave.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.radillo.playerwave.core.model.VisualizerSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

interface VisualizerSettingsRepository {
    val settings: Flow<VisualizerSettings>
    suspend fun update(transform: (VisualizerSettings) -> VisualizerSettings)
}

private val Context.visualizerDataStore: DataStore<Preferences> by preferencesDataStore(name = "visualizer_settings")

@Singleton
class DataStoreVisualizerSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) : VisualizerSettingsRepository {

    private val store = context.visualizerDataStore

    override val settings: Flow<VisualizerSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs.toSettings() }

    override suspend fun update(transform: (VisualizerSettings) -> VisualizerSettings) {
        store.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[KEY_SCENE] = next.sceneId
            prefs[KEY_SENSITIVITY] = next.sensitivity
            prefs[KEY_INTENSITY] = next.intensity
            prefs[KEY_SPEED] = next.speed
            prefs[KEY_DENSITY] = next.density
            prefs[KEY_PALETTE] = next.paletteId
            prefs[KEY_SYNC] = next.syncOffsetMs
            prefs[KEY_KEEP_SCREEN_ON] = next.keepScreenOn
            prefs[KEY_AUTO_QUALITY] = next.autoQuality
        }
    }

    private fun Preferences.toSettings(): VisualizerSettings {
        val defaults = VisualizerSettings()
        return VisualizerSettings(
            sceneId = this[KEY_SCENE] ?: defaults.sceneId,
            sensitivity = this[KEY_SENSITIVITY] ?: defaults.sensitivity,
            intensity = this[KEY_INTENSITY] ?: defaults.intensity,
            speed = this[KEY_SPEED] ?: defaults.speed,
            density = this[KEY_DENSITY] ?: defaults.density,
            paletteId = this[KEY_PALETTE] ?: defaults.paletteId,
            syncOffsetMs = this[KEY_SYNC] ?: defaults.syncOffsetMs,
            keepScreenOn = this[KEY_KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
            autoQuality = this[KEY_AUTO_QUALITY] ?: defaults.autoQuality,
        )
    }

    private companion object {
        val KEY_SCENE = stringPreferencesKey("scene")
        val KEY_SENSITIVITY = floatPreferencesKey("sensitivity")
        val KEY_INTENSITY = floatPreferencesKey("intensity")
        val KEY_SPEED = floatPreferencesKey("speed")
        val KEY_DENSITY = floatPreferencesKey("density")
        val KEY_PALETTE = stringPreferencesKey("palette")
        val KEY_SYNC = intPreferencesKey("sync_offset_ms")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEY_AUTO_QUALITY = booleanPreferencesKey("auto_quality")
    }
}
