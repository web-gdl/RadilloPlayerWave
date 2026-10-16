package com.radillo.playerwave.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.common.Player
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Estado del reproductor: aleatorio, repetición, carpeta seleccionada. */
data class PlaybackPreferences(
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,  // 0=OFF, 1=ALL, 2=ONE
    val currentFolderId: String? = null,  // carpeta o colección seleccionada
)

interface PlaybackPreferencesRepository {
    fun snapshot(): PlaybackPreferences
    fun save(prefs: PlaybackPreferences)
}

/** Guarda estado del reproductor entre sesiones en `SharedPreferences`. */
@Singleton
class SharedPlaybackPreferencesRepository @Inject constructor(
    @ApplicationContext context: Context,
) : PlaybackPreferencesRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun snapshot(): PlaybackPreferences = PlaybackPreferences(
        shuffleEnabled = prefs.getBoolean(KEY_SHUFFLE, false),
        repeatMode = prefs.getInt(KEY_REPEAT_MODE, Player.REPEAT_MODE_OFF),
        currentFolderId = prefs.getString(KEY_FOLDER_ID, null),
    )

    override fun save(prefs: PlaybackPreferences) {
        this.prefs.edit()
            .putBoolean(KEY_SHUFFLE, prefs.shuffleEnabled)
            .putInt(KEY_REPEAT_MODE, prefs.repeatMode)
            .putString(KEY_FOLDER_ID, prefs.currentFolderId)
            .apply()
    }

    companion object {
        private const val FILE = "playback_prefs"
        private const val KEY_SHUFFLE = "shuffle_enabled"
        private const val KEY_REPEAT_MODE = "repeat_mode"
        private const val KEY_FOLDER_ID = "current_folder_id"
    }
}
