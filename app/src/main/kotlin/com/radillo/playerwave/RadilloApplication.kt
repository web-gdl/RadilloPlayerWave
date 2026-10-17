package com.radillo.playerwave

import android.app.Application
import com.radillo.playerwave.core.device.DeviceProfile
import com.radillo.playerwave.core.diagnostics.EventLog
import com.radillo.playerwave.core.diagnostics.RenderMode
import com.radillo.playerwave.core.ui.CoverOverrides
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
    @Inject lateinit var profile: DeviceProfile

    private val scope = MainScope()

    override fun onCreate() {
        // Lo primero: sin adb, el registro y la traza del último cierre son la única pista.
        EventLog.init(this)
        EventLog.installCrashRecorder()
        super.onCreate()
        RenderMode.init(this, lowEndDevice = profile.lowEnd)
        CoverOverrides.init(this)
        EventLog.log("Proceso iniciado (${BuildConfig.VERSION_NAME})")
        // Restaura el ecualizador guardado. El controlador espera a que el servicio publique
        // la sesión de audio, así que es seguro llamarlo antes de que exista el reproductor.
        scope.launch {
            audioEffects.apply(effectsSettings.settings.first())
        }
    }
}
