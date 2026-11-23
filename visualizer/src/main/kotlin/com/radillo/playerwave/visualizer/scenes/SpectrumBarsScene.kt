package com.radillo.playerwave.visualizer.scenes

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.radillo.playerwave.visualizer.analysis.AudioFrame
import com.radillo.playerwave.visualizer.engine.Argb
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Escena 1: **barras LED** al estilo de un ecualizador Hi-Fi retro. Cada barra es una columna de
 * [ROWS] segmentos verde → ámbar → rojo que se encienden con el nivel de su banda (cada segmento
 * son 3 dB de la escala −60..0 dB del analizador); el pico se queda encendido un momento y luego
 * cae. Usa siempre los colores clásicos de LED: la paleta elegida no la tiñe.
 *
 * Para que sea barata con la CPU del autoestéreo **no dibuja segmento a segmento**: en [rebuild]
 * pinta una vez el panel (con los segmentos apagados y las escalas de dB y Hz), una columna con
 * todos los segmentos encendidos y su resplandor difuminado; cada frame solo copia el trozo
 * encendido de esa columna en cada barra. Cuando el gobernador baja la calidad se apaga el
 * resplandor y bajan las barras.
 */
class SpectrumBarsScene : CanvasScene(id = ID, displayName = "Barras LED") {

    private var columns = 32
    private var levels = FloatArray(columns)
    private var peaks = FloatArray(columns)
    private var peakHold = FloatArray(columns)
    private var targets = FloatArray(columns)

    private var panel: Bitmap? = null
    private var litStrip: Bitmap? = null
    private var glowStrip: Bitmap? = null

    private var gridLeft = 0f
    private var gridTop = 0f
    private var gridHeight = 0f
    private var cellW = 0f
    private var cellH = 0f
    private var colGap = 0f
    private var rowGap = 0f
    private var glowPad = 0
    private var stripW = 0
    private var stripH = 0
    private var ready = false
    private var drawGlow = true

    private val src = Rect()
    private val dst = RectF()
    private val blitPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val glowBlitPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    override fun rebuild() {
        val newCount = (52f * params.effectiveDensity).roundToInt().coerceIn(12, 64)
        if (newCount != columns || levels.size != newCount) {
            columns = newCount
            levels = FloatArray(columns)
            peaks = FloatArray(columns)
            peakHold = FloatArray(columns)
            targets = FloatArray(columns)
        }
        drawGlow = params.quality >= GLOW_MIN_QUALITY
        ready = false
        if (!hasSize) return
        val w = width.toFloat()
        val h = height.toFloat()
        val labels = width >= 480 && height >= 260
        val outer = max(4f, min(w, h) * 0.026f)
        gridLeft = if (labels) w * 0.09f else outer * 2f
        val gridRight = w - if (labels) w * 0.035f else outer * 2f
        gridTop = if (labels) h * 0.08f else outer * 2f
        val gridBottom = h - if (labels) h * 0.085f else outer * 2f
        gridHeight = gridBottom - gridTop
        colGap = max(2f, w * 0.006f)
        rowGap = max(1.5f, h * 0.0075f)
        cellW = ((gridRight - gridLeft) - colGap * (columns - 1)) / columns
        cellH = (gridHeight - rowGap * (ROWS - 1)) / ROWS
        if (cellW < 1f || cellH < 1f) return

        stripW = ceil(cellW).toInt().coerceAtLeast(1)
        stripH = ceil(gridHeight).toInt().coerceAtLeast(1)
        glowPad = max(2f, min(cellW, cellH) * 0.45f).roundToInt()
        val radius = min(cellW, cellH) * 0.18f

        // Columna con todos los segmentos encendidos.
        // En las pruebas de JVM (stubs del SDK) createBitmap devuelve null: no hay nada que pintar.
        val lit: Bitmap = newBitmap(stripW, stripH) ?: return
        val cellPaint = fillPaint()
        val shine = fillPaint().apply { color = Argb.withAlpha(0xFFFFFFFF.toInt(), 0.22f) }
        Canvas(lit).also { c ->
            for (r in 0 until ROWS) {
                val top = cellTop(r)
                cellPaint.color = ledColor(r)
                c.drawRoundRect(0f, top, cellW, top + cellH, radius, radius, cellPaint)
                c.drawRect(cellW * 0.12f, top + cellH * 0.1f, cellW * 0.88f, top + cellH * 0.1f + max(1f, cellH * 0.1f), shine)
            }
        }
        litStrip = lit

        // Resplandor: la misma columna, difuminada, con margen alrededor.
        val glow: Bitmap = newBitmap(stripW + glowPad * 2, stripH + glowPad * 2) ?: return
        val blur = fillPaint().apply { maskFilter = BlurMaskFilter(glowPad * 0.9f, BlurMaskFilter.Blur.NORMAL) }
        Canvas(glow).also { c ->
            for (r in 0 until ROWS) {
                val top = cellTop(r) + glowPad
                blur.color = ledColor(r)
                c.drawRect(glowPad.toFloat(), top, glowPad + cellW, top + cellH, blur)
            }
        }
        glowStrip = glow
        glowBlitPaint.alpha = alpha255(0.55f)

        // Panel: fondo, bisel, segmentos apagados y escalas.
        val back: Bitmap = newBitmap(width, height) ?: return
        Canvas(back).also { c ->
            c.drawColor(PANEL_OUTSIDE)
            val face = fillPaint().apply {
                fillShader = LinearGradient(0f, 0f, 0f, h, PANEL_TOP, PANEL_BOTTOM, Shader.TileMode.CLAMP)
            }
            c.drawRect(outer, outer, w - outer, h - outer, face)
            val bezel = strokePaint(max(1f, outer * 0.15f), rounded = false).apply { color = BEZEL }
            c.drawRect(outer, outer, w - outer, h - outer, bezel)
            for (r in 0 until ROWS) {
                cellPaint.color = Argb.withAlpha(ledColor(r), 0.09f)
                val top = gridTop + cellTop(r)
                for (i in 0 until columns) {
                    val x = columnX(i)
                    c.drawRoundRect(x, top, x + cellW, top + cellH, radius, radius, cellPaint)
                }
            }
            if (labels) drawScales(c, w, h, outer)
        }
        panel = back
        ready = true
    }

    private fun drawScales(c: Canvas, w: Float, h: Float, outer: Float) {
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = (h * 0.028f).coerceIn(9f, 18f)
        }
        val face: Typeface? = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        text.typeface = face
        val tick = fillPaint().apply { color = TICK }
        text.textAlign = Paint.Align.RIGHT
        for ((row, label) in DB_MARKS) {
            val y = gridTop + cellTop(row) + cellH * 0.5f
            text.color = Argb.lerp(ledColor(row), 0xFFFFFFFF.toInt(), 0.25f)
            c.drawText(label, gridLeft - w * 0.022f, y + text.textSize * 0.35f, text)
            c.drawRect(gridLeft - w * 0.014f, y - 0.75f, gridLeft - w * 0.006f, y + 0.75f, tick)
        }
        text.color = MUTED
        text.textAlign = Paint.Align.LEFT
        c.drawText("dB", outer * 2.2f, gridTop - h * 0.025f, text)
        // Frecuencias reales: las bandas del analizador son logarítmicas entre 30 Hz y 16 kHz.
        text.textAlign = Paint.Align.CENTER
        val span = ln(MAX_HZ / MIN_HZ)
        for ((hz, label) in HZ_MARKS) {
            val f = (ln(hz / MIN_HZ) / span).coerceIn(0f, 1f)
            val i = (f * (columns - 1)).roundToInt()
            val x = (columnX(i) + cellW * 0.5f).coerceIn(gridLeft + text.textSize, w - outer * 2f - text.textSize * 1.6f)
            c.drawText(label, x, h - h * 0.035f, text)
        }
    }

    private fun newBitmap(w: Int, h: Int): Bitmap? {
        val bitmap: Bitmap? = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        return bitmap
    }

    /** Parte superior del segmento [row] (0 = el de abajo) dentro de la columna. */
    private fun cellTop(row: Int): Float = gridHeight - (row + 1) * cellH - row * rowGap

    private fun columnX(i: Int): Float = gridLeft + i * (cellW + colGap)

    override fun update(frame: AudioFrame, deltaSeconds: Float) {
        val bands = frame.bands
        val n = bands.size
        val sensitivity = params.sensitivity
        for (i in 0 until columns) {
            val pos = if (columns > 1) i.toFloat() / (columns - 1) * (n - 1) else 0f
            targets[i] = (sampleLinear(bands, pos) * sensitivity).coerceIn(0f, 1f)
        }
        val rise = smoothing(deltaSeconds, 30f * params.speed)
        val fall = smoothing(deltaSeconds, 7f * params.speed)
        val drop = PEAK_FALL * params.speed * deltaSeconds
        for (i in 0 until columns) {
            val v = follow(levels[i], targets[i], rise, fall)
            levels[i] = v
            if (v >= peaks[i]) {
                peaks[i] = v
                peakHold[i] = PEAK_HOLD_SECONDS
            } else if (peakHold[i] > 0f) {
                peakHold[i] -= deltaSeconds
            } else {
                peaks[i] = max(v, peaks[i] - drop)
            }
        }
    }

    override fun draw(canvas: Canvas) {
        if (!ready) return
        val back = panel ?: return
        val lit = litStrip ?: return
        val glow = glowStrip ?: return
        val intensity = params.intensity.coerceIn(0.1f, 2f)
        canvas.drawBitmap(back, 0f, 0f, null)

        if (drawGlow) {
            for (i in 0 until columns) {
                val on = segments(levels[i] * intensity)
                if (on == 0) continue
                val top = floor(cellTop(on - 1)).toInt().coerceIn(0, stripH)
                val x = columnX(i) - glowPad
                src.set(0, top, glow.width, glow.height)
                dst.set(x, gridTop - glowPad + top, x + glow.width, gridTop - glowPad + glow.height)
                canvas.drawBitmap(glow, src, dst, glowBlitPaint)
            }
        }
        for (i in 0 until columns) {
            val x = columnX(i)
            val on = segments(levels[i] * intensity)
            if (on > 0) {
                val top = floor(cellTop(on - 1)).toInt().coerceIn(0, stripH)
                src.set(0, top, stripW, stripH)
                dst.set(x, gridTop + top, x + stripW, gridTop + stripH)
                canvas.drawBitmap(lit, src, dst, blitPaint)
            }
            val peak = segments(peaks[i] * intensity)
            if (peak > on) {
                val top = floor(cellTop(peak - 1)).toInt().coerceIn(0, stripH)
                val bottom = ceil(cellTop(peak - 1) + cellH).toInt().coerceIn(top, stripH)
                src.set(0, top, stripW, bottom)
                dst.set(x, gridTop + top, x + stripW, gridTop + bottom)
                canvas.drawBitmap(lit, src, dst, blitPaint)
            }
        }
    }

    override fun onDetach() {
        // Sin recycle(): un lienzo con aceleración podría seguir referenciándolos; basta soltarlos.
        panel = null
        litStrip = null
        glowStrip = null
        ready = false
    }

    companion object {
        const val ID = "spectrum"

        /** Segmentos por barra: con la escala −60..0 dB del analizador, 3 dB cada uno. */
        const val ROWS = 20

        /** Por debajo de esta calidad del gobernador se apaga el resplandor. */
        const val GLOW_MIN_QUALITY = 0.6f

        private const val PEAK_HOLD_SECONDS = 0.6f
        private const val PEAK_FALL = 0.9f
        private const val MIN_HZ = 30f
        private const val MAX_HZ = 16_000f

        private const val GREEN = 0xFF2FE05A.toInt()
        private const val AMBER = 0xFFFFC21A.toInt()
        private const val RED = 0xFFFF3B30.toInt()
        private const val PANEL_OUTSIDE = 0xFF07090C.toInt()
        private const val PANEL_TOP = 0xFF10141A.toInt()
        private const val PANEL_BOTTOM = 0xFF090B0F.toInt()
        private const val BEZEL = 0xFF1B222C.toInt()
        private const val TICK = 0xFF2A323D.toInt()
        private const val MUTED = 0xFF7D8794.toInt()

        private val DB_MARKS = listOf(19 to "0", 17 to "-6", 15 to "-12", 11 to "-24", 7 to "-36", 3 to "-48")
        private val HZ_MARKS = listOf(60f to "60", 250f to "250", 1000f to "1k", 4000f to "4k", 16_000f to "16k Hz")

        /** Verde hasta −21 dB, ámbar hasta −9 dB y rojo los últimos tres segmentos. */
        fun ledColor(row: Int): Int = when {
            row >= ROWS - 3 -> RED
            row >= ROWS - 7 -> AMBER
            else -> GREEN
        }

        /** Segmentos encendidos para un nivel 0..1. */
        fun segments(level: Float): Int = (level * ROWS + 0.35f).toInt().coerceIn(0, ROWS)
    }
}
