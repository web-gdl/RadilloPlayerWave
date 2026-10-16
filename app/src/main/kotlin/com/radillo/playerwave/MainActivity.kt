package com.radillo.playerwave

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.radillo.playerwave.core.device.DeviceProfile
import com.radillo.playerwave.data.settings.AppSettingsRepository
import com.radillo.playerwave.ui.RadilloApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var profile: DeviceProfile
    @Inject lateinit var appSettings: AppSettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Pantallas de coche (sin acelerómetro o en modo coche): horizontal desde el primer frame.
        applyOrientation(profile.fixedLandscape)
        // El tema (claro/oscuro y acento de la carátula) se decide dentro de RadilloApp
        // a partir de los ajustes persistidos.
        setContent { RadilloApp() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appSettings.settings
                    .map { it.lockLandscape ?: profile.fixedLandscape }
                    .distinctUntilChanged()
                    .collect(::applyOrientation)
            }
        }
    }

    private fun applyOrientation(lockLandscape: Boolean) {
        val wanted = if (lockLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        if (requestedOrientation != wanted) requestedOrientation = wanted
    }
}
