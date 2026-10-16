package com.radillo.playerwave

import android.app.Application
import com.radillo.playerwave.data.settings.AudioEffectsSettingsRepository
import com.radillo.playerwave.player.audiofx.AudioEffectsController
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RadilloApplication : Application() {

    @Inject lateinit var audioEffects: AudioEffectsController
    @Inject lateinit var effectsSettings: AudioEffectsSettingsRepository

    private val scope = MainScope()

    override fun onCreate() {
        super.onCreate()
        // Restaura el ecualizador guardado. El controlador espera a que el servicio publique
        // la sesión de audio, así que es seguro llamarlo antes de que exista el reproductor.
        scope.launch {
            audioEffects.apply(effectsSettings.settings.first())
        }
    }
}
