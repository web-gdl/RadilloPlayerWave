package com.radillo.playerwave.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.radillo.playerwave.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * El color de acento de toda la app: el de la portada que suena (o el fijo si no hay portada o el
 * ajuste «Color de la portada» está apagado), ya ajustado para verse sobre el fondo. Lo fija
 * [com.radillo.playerwave.MainActivity]; 0 = todavía sin calcular.
 */
object AppAccent {
    private val _color = MutableStateFlow(0)
    val color: StateFlow<Int> = _color

    fun set(color: Int) {
        _color.value = color
    }

    /** Acento vigente, o el fijo de la app (visible sobre el fondo) si aún no hay uno. */
    fun current(context: Context): Int = _color.value.takeIf { it != 0 }
        ?: AccentColors.visible(context.getColor(R.color.accent), context.getColor(R.color.window_background))
}

/**
 * Pinta con el acento las vistas marcadas con una etiqueta de acento (puesta en los estilos), y todos
 * los interruptores y deslizadores. Así una sola llamada sobre la raíz de una pantalla la deja con un
 * único color, el de la portada, en lugar del celeste fijo de antes (ley de similitud).
 *
 * Etiquetas: [TEXT] (títulos de sección), [BUTTON] (botón principal), [CHIP] (opción seleccionable),
 * [TAB] (pestaña con subrayado), [ICON] (icono en acento), [ROW] / [ROW_TITLE] (fila de la canción que
 * suena, por estado «activado»).
 */
object AccentPainter {
    const val TEXT = "accent_text"
    const val BUTTON = "accent_button"
    const val CHIP = "accent_chip"
    const val TAB = "accent_tab"
    const val ICON = "accent_icon"
    const val ROW = "accent_row"
    const val ROW_TITLE = "accent_row_title"

    fun paint(root: View, accent: Int) {
        val context = root.context
        val corner = context.resources.getDimension(R.dimen.corner)
        val onAccent = AccentColors.onAccent(accent)
        val secondary = context.getColor(R.color.text_secondary)
        val primary = context.getColor(R.color.text_primary)
        val surfaceAlt = context.getColor(R.color.surface_alt)
        val off = ColorUtils.setAlphaComponent(secondary, 0x80)
        walk(root) { view ->
            when (view.tag) {
                TEXT -> (view as? TextView)?.setTextColor(accent)
                BUTTON -> {
                    view.background = rounded(accent, corner)
                    (view as? TextView)?.let {
                        it.setTextColor(onAccent)
                        it.compoundDrawableTintList = ColorStateList.valueOf(onAccent)
                    }
                }
                CHIP -> {
                    view.background = StateListDrawable().apply {
                        addState(intArrayOf(android.R.attr.state_selected), rounded(accent, corner))
                        addState(intArrayOf(), rounded(surfaceAlt, corner))
                    }
                    (view as? TextView)?.setTextColor(AccentColors.selectedStates(onAccent, primary))
                }
                TAB -> {
                    val indicator = LayerDrawable(arrayOf(ColorDrawable(accent))).apply {
                        setLayerHeight(0, context.resources.getDimensionPixelSize(R.dimen.tab_indicator))
                        setLayerGravity(0, Gravity.BOTTOM)
                    }
                    view.background = StateListDrawable().apply {
                        addState(intArrayOf(android.R.attr.state_selected), indicator)
                        addState(intArrayOf(), ColorDrawable(0))
                    }
                    (view as? TextView)?.setTextColor(AccentColors.selectedStates(accent, secondary))
                }
                ICON -> (view as? ImageView)?.imageTintList = ColorStateList.valueOf(accent)
                ROW -> view.background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_activated), ColorDrawable(ColorUtils.setAlphaComponent(accent, 0x24)))
                    addState(intArrayOf(), ColorDrawable(0))
                }
                ROW_TITLE -> (view as? TextView)?.setTextColor(AccentColors.activatedStates(accent, primary))
            }
            when (view) {
                is Switch -> {
                    view.thumbTintList = checkedStates(accent, context.getColor(R.color.surface_alt).let { ColorUtils.blendARGB(it, 0xFFFFFFFF.toInt(), 0.75f) })
                    view.trackTintList = checkedStates(ColorUtils.setAlphaComponent(accent, 0x80), off)
                }
                is SeekBar -> {
                    view.progressTintList = ColorStateList.valueOf(accent)
                    view.thumbTintList = ColorStateList.valueOf(accent)
                }
            }
        }
    }

    /** Como [paint], pero no hace nada si [root] ya está pintada con este [accent] (filas de listas). */
    fun paintOnce(root: View, accent: Int) {
        if (root.getTag(R.id.painted_accent) == accent) return
        paint(root, accent)
        root.setTag(R.id.painted_accent, accent)
    }

    private fun checkedStates(checked: Int, normal: Int) = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(checked, normal),
    )

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        cornerRadius = radius
        setColor(color)
    }

    private fun walk(view: View, action: (View) -> Unit) {
        action(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i), action)
    }
}
