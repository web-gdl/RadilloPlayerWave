package com.radillo.playerwave.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.radillo.playerwave.core.model.VisualizerSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface VisualizerSettingsRepository {
    val settings: Flow<VisualizerSettings>

    /** Valor actual, leído en el momento (síncrono, sin E/S tras la primera carga). */
    fun snapshot(): VisualizerSettings
    suspend fun update(transform: (VisualizerSettings) -> VisualizerSettings)
}

/** Ajustes del visualizador en `SharedPreferences` (lectura inmediata, sin DataStore). */
@Singleton
class PrefsVisualizerSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) : VisualizerSettingsRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences("visualizer_settings", Context.MODE_PRIVATE)
    private val lock = Any()

    override val settings: Flow<VisualizerSettings> = prefs.observe { it.toSettings() }

    override fun snapshot(): VisualizerSettings = prefs.toSettings()

    override suspend fun update(transform: (VisualizerSettings) -> VisualizerSettings) {
        synchronized(lock) {
            val next = transform(prefs.toSettings())
            prefs.edit()
                .putString(KEY_SCENE, next.sceneId)
                .putFloat(KEY_SENSITIVITY, next.sensitivity)
                .putFloat(KEY_INTENSITY, next.intensity)
                .putFloat(KEY_SPEED, next.speed)
                .putFloat(KEY_DENSITY, next.density)
                .putString(KEY_PALETTE, next.paletteId)
                .putInt(KEY_SYNC, next.syncOffsetMs)
                .putBoolean(KEY_KEEP_SCREEN_ON, next.keepScreenOn)
                .putBoolean(KEY_AUTO_QUALITY, next.autoQuality)
                .putInt(KEY_MAX_FPS, next.maxFps)
                .apply()
        }
    }

    private fun SharedPreferences.toSettings(): VisualizerSettings {
        val d = VisualizerSettings()
        return VisualizerSettings(
            sceneId = getString(KEY_SCENE, null) ?: d.sceneId,
            sensitivity = getFloat(KEY_SENSITIVITY, d.sensitivity),
            intensity = getFloat(KEY_INTENSITY, d.intensity),
            speed = getFloat(KEY_SPEED, d.speed),
            density = getFloat(KEY_DENSITY, d.density),
            paletteId = getString(KEY_PALETTE, null) ?: d.paletteId,
            syncOffsetMs = getInt(KEY_SYNC, d.syncOffsetMs),
            keepScreenOn = getBoolean(KEY_KEEP_SCREEN_ON, d.keepScreenOn),
            autoQuality = getBoolean(KEY_AUTO_QUALITY, d.autoQuality),
            maxFps = getInt(KEY_MAX_FPS, d.maxFps),
        )
    }

    private companion object {
        const val KEY_SCENE = "scene"
        const val KEY_SENSITIVITY = "sensitivity"
        const val KEY_INTENSITY = "intensity"
        const val KEY_SPEED = "speed"
        const val KEY_DENSITY = "density"
        const val KEY_PALETTE = "palette"
        const val KEY_SYNC = "sync_offset_ms"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_AUTO_QUALITY = "auto_quality"
        const val KEY_MAX_FPS = "max_fps"
    }
}
