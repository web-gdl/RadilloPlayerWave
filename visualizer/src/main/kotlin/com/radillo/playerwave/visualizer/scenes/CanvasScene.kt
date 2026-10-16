package com.radillo.playerwave.visualizer.scenes

import android.graphics.Paint
import android.graphics.Shader
import com.radillo.playerwave.visualizer.engine.Palettes
import com.radillo.playerwave.visualizer.engine.RenderBackend
import com.radillo.playerwave.visualizer.engine.Scene
import com.radillo.playerwave.visualizer.engine.SceneParams
import com.radillo.playerwave.visualizer.engine.VisualPalette
import kotlin.math.exp

/**
 * Base para escenas dibujadas con `android.graphics.Canvas`: guarda parámetros, paleta y tamaño,
 * y ofrece utilidades de suavizado y pinceles. Las subclases implementan [update] y [draw] sin
 * asignar memoria por frame (los buffers, pinceles y degradados se crean en [rebuild]).
 */
abstract class CanvasScene(
    override val id: String,
    override val displayName: String,
) : Scene {

    override val backend: RenderBackend = RenderBackend.CANVAS

    protected var params: SceneParams = SceneParams()
        private set
    protected var palette: VisualPalette = Palettes.AURORA
        private set
    protected var width: Int = 0
        private set
    protected var height: Int = 0
        private set

    override fun onAttach(params: SceneParams) {
        this.params = params
        palette = Palettes.byId(params.paletteId)
        rebuild()
    }

    override fun onParamsChanged(params: SceneParams) {
        val paletteChanged = params.paletteId != this.params.paletteId
        val densityChanged = params.effectiveDensity != this.params.effectiveDensity
        this.params = params
        if (paletteChanged) palette = Palettes.byId(params.paletteId)
        if (paletteChanged || densityChanged) rebuild()
    }

    override fun onResize(widthPx: Int, heightPx: Int) {
        if (widthPx == width && heightPx == height) return
        width = widthPx
        height = heightPx
        rebuild()
    }

    override fun onDetach() = Unit

    /** Reconstruye buffers, pinceles y degradados dependientes de tamaño, densidad o paleta. */
    protected abstract fun rebuild()

    protected val hasSize: Boolean get() = width > 0 && height > 0

    companion object {
        /** Coeficiente de aproximación exponencial para `dt` y una tasa (1/s). */
        fun smoothing(dt: Float, rate: Float): Float = (1f - exp(-dt * rate)).coerceIn(0f, 1f)

        fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

        /** Muestra lineal de `values` en la posición fraccional `pos` ∈ [0, size-1]. */
        fun sampleLinear(values: FloatArray, pos: Float): Float {
            if (values.isEmpty()) return 0f
            val clamped = pos.coerceIn(0f, (values.size - 1).toFloat())
            val i = clamped.toInt()
            val f = clamped - i
            val a = values[i]
            val b = values[minOf(i + 1, values.size - 1)]
            return a + (b - a) * f
        }

        /** Pincel de relleno con antialias. */
        fun fillPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        /** Pincel de trazo con antialias y extremos/uniones redondeados. */
        fun strokePaint(width: Float, rounded: Boolean = true): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = width
            if (rounded) {
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
        }

        /** Alfa en [0, 1] → 0..255 para `Paint.alpha`. */
        fun alpha255(alpha: Float): Int = (alpha.coerceIn(0f, 1f) * 255f + 0.5f).toInt()

        /**
         * `Paint.setShader` devuelve el `Shader` (no `void`), así que Kotlin no genera una propiedad
         * asignable; esta extensión permite `paint.fillShader = gradient`.
         */
        var Paint.fillShader: Shader?
            get() = shader
            set(value) {
                setShader(value)
            }
    }
}
