package com.radillo.playerwave

import android.app.Application
import com.radillo.playerwave.core.device.DeviceProfile
import com.radillo.playerwave.core.diagnostics.EventLog
import com.radillo.playerwave.core.diagnostics.RenderMode
import com.radillo.playerwave.core.ui.CoverOverrides
import com.radillo.playerwave.data.tags.TagWriter
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RadilloApplication : Application() {

    @Inject lateinit var profile: DeviceProfile
    @Inject lateinit var tagWriter: TagWriter

    private val scope = MainScope()

    override fun onCreate() {
        // Lo primero: sin adb, el registro y la traza del último cierre son la única pista.
        EventLog.init(this)
        EventLog.installCrashRecorder()
        super.onCreate()
        RenderMode.init(this, lowEndDevice = profile.lowEnd)
        CoverOverrides.init(this)
        // Reversa, llave y teclas del volante que anuncia el estéreo (solo equipos Jancar).
        CarSignalsReceiver.register(this)
        EventLog.log("Proceso iniciado (${BuildConfig.VERSION_NAME})")
        // Si una edición de etiquetas se cortó (se quitó la llave a mitad), se termina o se deshace.
        scope.launch { tagWriter.recoverPending() }
    }
}
