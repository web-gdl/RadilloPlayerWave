package com.radillo.playerwave.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.graphics.ColorUtils

/**
 * Color de acento dinámico (sale de la portada de la canción que suena): el mismo para el botón de
 * reproducir, la barra superior y la barra de progreso, de modo que toda la app «sigue» a la música.
 */
object AccentColors {

    private const val MIN_CONTRAST = 3.0

    /**
     * Acerca el acento al blanco (fondo oscuro) o al negro (fondo claro) hasta que se distinga del
     * [background]: una portada oscura no debe dejar los botones invisibles.
     */
    fun visible(accent: Int, background: Int): Int {
        val towards = if (ColorUtils.calculateLuminance(background) < 0.5) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        var color = accent or 0xFF000000.toInt()
        var step = 0
        while (ColorUtils.calculateContrast(color, background) < MIN_CONTRAST && step < 12) {
            color = ColorUtils.blendARGB(color, towards, 0.08f)
            step++
        }
        return color
    }

    /** Color del texto o icono sobre un fondo de acento: oscuro o blanco, el que más contraste dé. */
    fun onAccent(accent: Int): Int {
        val dark = 0xFF071014.toInt()
        val light = 0xFFFFFFFF.toInt()
        return if (ColorUtils.calculateContrast(dark, accent) >= ColorUtils.calculateContrast(light, accent)) dark else light
    }

    /** Estados «seleccionado» / normal para tintes de iconos y texto. */
    fun selectedStates(selected: Int, normal: Int): ColorStateList = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
        intArrayOf(selected, normal),
    )

    /** Fondo de una sección de la barra superior (botón redondeado): seleccionada, pulsada, con foco o plana. */
    fun pillBackground(accent: Int, radiusPx: Float, strokePx: Int): StateListDrawable = StateListDrawable().apply {
        fun shape(fill: Int?, stroke: Int? = null) = GradientDrawable().apply {
            cornerRadius = radiusPx
            if (fill != null) setColor(fill) else setColor(0)
            if (stroke != null) setStroke(strokePx, stroke)
        }
        addState(intArrayOf(android.R.attr.state_selected), shape(accent))
        addState(intArrayOf(android.R.attr.state_pressed), shape(ColorUtils.setAlphaComponent(accent, 0x40)))
        addState(intArrayOf(android.R.attr.state_focused), shape(null, accent))
        addState(intArrayOf(), shape(null))
    }

    /** Fondo de una sección de la barra inferior (móvil): tinte suave del acento cuando está seleccionada. */
    fun tabBackground(accent: Int): StateListDrawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_selected), ColorDrawable(ColorUtils.setAlphaComponent(accent, 0x2E)))
        addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(ColorUtils.setAlphaComponent(accent, 0x1F)))
        addState(intArrayOf(), ColorDrawable(0))
    }

    /** Pinta icono y texto de una sección (hijos del contenedor, que heredan su estado). */
    fun tintContent(item: ViewGroup, colors: ColorStateList) {
        for (i in 0 until item.childCount) {
            when (val child: View = item.getChildAt(i)) {
                is ImageView -> child.imageTintList = colors
                is TextView -> child.setTextColor(colors)
            }
        }
    }

    /**
     * Aplica el acento a la barra de navegación y al progreso del mini reproductor. [search] solo existe
     * en la barra superior de pantallas anchas ([wide]); en la inferior (móvil) las secciones llevan el
     * acento en icono y texto sobre un tinte suave.
     */
    fun styleNav(
        context: Context,
        sections: List<ViewGroup>,
        search: ViewGroup?,
        favoritesIcon: ImageView,
        miniProgress: ProgressBar,
        accent: Int?,
        wide: Boolean,
    ) {
        val visible = visible(
            accent ?: context.getColor(com.radillo.playerwave.R.color.accent),
            context.getColor(if (wide) com.radillo.playerwave.R.color.window_background else com.radillo.playerwave.R.color.surface),
        )
        val secondary = context.getColor(com.radillo.playerwave.R.color.text_secondary)
        if (wide) {
            val d = context.resources.displayMetrics.density
            val colors = selectedStates(onAccent(visible), secondary)
            (sections + listOfNotNull(search)).forEach {
                it.background = pillBackground(visible, 24 * d, (2 * d).toInt())
                tintContent(it, colors)
            }
        } else {
            val colors = selectedStates(visible, secondary)
            sections.forEach {
                it.background = tabBackground(visible)
                tintContent(it, colors)
            }
        }
        // El corazón de Favoritos va siempre en acento: es una acción, no una sección que se «selecciona».
        favoritesIcon.imageTintList = ColorStateList.valueOf(visible)
        miniProgress.progressTintList = ColorStateList.valueOf(visible)
    }
}
