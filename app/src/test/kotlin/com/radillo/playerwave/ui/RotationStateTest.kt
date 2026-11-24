package com.radillo.playerwave.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Parcelable
import android.util.SparseArray
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertWithMessage
import com.radillo.playerwave.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Girar la pantalla del móvil (vertical ↔ horizontal) cambia de layout (`layout` ↔ `layout-w600dp`).
 * Android guarda el estado de cada vista por su id y lo devuelve a la vista con el mismo id del
 * otro layout: si es de otra clase (p. ej. LinearLayout ↔ ScrollView) la app se cierra. Pasó en
 * Xiaomi al volver a vertical en «Reproduciendo» (0.23.0). Esta prueba simula el giro de ida y
 * vuelta en todos los layouts que tienen variante.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RotationStateTest {

    private val layoutsWithVariants = listOf(
        R.layout.activity_main,
        R.layout.fragment_now_playing,
        R.layout.fragment_tag_editor,
    )

    private fun context(widthDp: Int, heightDp: Int): Context {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(app.resources.configuration).apply {
            screenWidthDp = widthDp
            screenHeightDp = heightDp
            smallestScreenWidthDp = minOf(widthDp, heightDp)
            orientation = if (widthDp > heightDp) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        }
        return ContextThemeWrapper(app.createConfigurationContext(config), R.style.Theme_RadilloPlayerWave)
    }

    private fun inflate(context: Context, layout: Int): View {
        val parent = FrameLayout(context)
        val view = LayoutInflater.from(context).inflate(layout, parent, false)
        parent.addView(view)
        return parent
    }

    private fun turn(layout: Int, from: Context, to: Context, saveContentGroup: Boolean = false) {
        val state = SparseArray<Parcelable>()
        val before = inflate(from, layout)
        if (saveContentGroup) before.findViewById<View>(R.id.contentGroup)?.isSaveEnabled = true
        before.saveHierarchyState(state)
        val after = inflate(to, layout)
        if (saveContentGroup) after.findViewById<View>(R.id.contentGroup)?.isSaveEnabled = true
        after.restoreHierarchyState(state)
    }

    /** Comprueba la causa: con el estado de `contentGroup` guardado (como hasta la 0.22.0), se cierra. */
    @Test
    fun `before the fix, turning back to portrait crashed in now playing`() {
        val portrait = context(411, 891)
        val landscape = context(891, 411)
        turn(R.layout.fragment_now_playing, portrait, landscape, saveContentGroup = true)
        var crash: RuntimeException? = null
        try {
            turn(R.layout.fragment_now_playing, landscape, portrait, saveContentGroup = true)
        } catch (e: RuntimeException) {
            crash = e
        }
        assertWithMessage("se esperaba el cierre de la 0.22.0 al volver a vertical").that(crash).isNotNull()
    }

    @Test
    fun `turning portrait to landscape and back restores every layout without crashing`() {
        val portrait = context(411, 891)
        val landscape = context(891, 411)
        for (layout in layoutsWithVariants) {
            val name = portrait.resources.getResourceEntryName(layout)
            try {
                turn(layout, portrait, landscape) // a horizontal
                turn(layout, landscape, portrait) // de vuelta a vertical (aquí se cerraba)
            } catch (e: RuntimeException) {
                assertWithMessage("$name: ${e.javaClass.simpleName}: ${e.message}").fail()
            }
        }
    }
}
