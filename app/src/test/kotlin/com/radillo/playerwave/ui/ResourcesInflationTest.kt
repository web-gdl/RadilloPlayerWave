package com.radillo.playerwave.ui

import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.radillo.playerwave.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Sin dispositivo en CI, esta prueba garantiza que todos los layouts se inflan y todos los
 * iconos vectoriales se cargan con el tema real, en claro y oscuro, en móvil (vertical) y en el
 * autoestéreo (1024×600 a 160 dpi, sw600dp, horizontal). Detecta atributos, estilos o trazos
 * vectoriales inválidos antes de llegar al equipo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ResourcesInflationTest {

    private val layouts = listOf(
        R.layout.activity_main,
        R.layout.view_mini_player,
        R.layout.item_row,
        R.layout.view_chip,
        R.layout.fragment_library,
        R.layout.fragment_track_list,
        R.layout.fragment_now_playing,
        R.layout.fragment_visualizer,
        R.layout.dialog_visualizer_settings,
        R.layout.fragment_equalizer,
        R.layout.fragment_settings,
        R.layout.fragment_diagnostics,
    )

    private val drawables = R.drawable::class.java.fields
        .filter { it.name.startsWith("ic_") || it.name.startsWith("tile_") || it.name.startsWith("bg_") }
        .map { it.getInt(null) }

    private fun inflateAll(night: Boolean) {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = Configuration(app.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        val context = ContextThemeWrapper(app.createConfigurationContext(config), R.style.Theme_RadilloPlayerWave)
        val inflater = LayoutInflater.from(context)
        val parent = FrameLayout(context)
        layouts.forEach { id ->
            val view = inflater.inflate(id, parent, false)
            assertThat(view).isNotNull()
        }
        assertThat(drawables).isNotEmpty()
        drawables.forEach { id -> assertThat(context.getDrawable(id)).isNotNull() }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-port-xxhdpi")
    fun phonePortraitLightAndDark() {
        inflateAll(night = false)
        inflateAll(night = true)
    }

    @Test
    @Config(qualifiers = "w1024dp-h600dp-land-mdpi")
    fun carHeadUnitLightAndDark() {
        inflateAll(night = false)
        inflateAll(night = true)
    }
}
