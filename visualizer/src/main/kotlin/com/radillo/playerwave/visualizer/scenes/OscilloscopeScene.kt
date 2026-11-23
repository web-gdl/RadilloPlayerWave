package com.radillo.playerwave.visualizer.scenes

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import com.radillo.playerwave.visualizer.analysis.AudioFrame
import com.radillo.playerwave.visualizer.engine.Argb
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Escena 3: **onda y osciloscopio**. Sobre una retícula de osciloscopio se dibujan los dos canales
 * (L y R) en el mismo eje, con disparo por cruce por cero para que la onda no «corra», un
 * resplandor suave y un relleno que se desvanece hacia el eje. L toma el primer color de la paleta
 * y R el último.
 *
 * Con calidad baja se quita primero el relleno y después el resplandor; también bajan los puntos.
 */
class OscilloscopeScene : CanvasScene(id = ID, displayName = "Onda y osciloscopio") {

    private var pointCount = 240
    private var leftPoints = FloatArray(pointCount)
    private var rightPoints = FloatArray(pointCount)
    private val leftPath = Path()
    private val rightPath = Path()
    private val fillPath = Path()
    private val gridPath = Path()
    private val axisPath = Path()
    private val triggerPath = Path()

    private val mainPaint = strokePaint(3f)
    private val glowPaint = strokePaint(12f)
    private val gridPaint = strokePaint(1f, rounded = false)
    private val axisPaint = strokePaint(1f, rounded = false)
    private val leftFill = fillPaint()
    private val rightFill = fillPaint()
    private val markerPaint = fillPaint()
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var leftColor = 0
    private var rightColor = 0
    private var level = 0f
    private var flash = 0f
    private var margin = 0f
    private var centerY = 0f
    private var span = 0f
    private var halfHeight = 0f
    private var drawFill = true
    private var drawGlow = true
    private var drawLabels = true

    override fun rebuild() {
        val newCount = (300f * params.effectiveDensity).roundToInt().coerceIn(64, 512)
        if (newCount != pointCount || leftPoints.size != newCount) {
            pointCount = newCount
            leftPoints = FloatArray(pointCount)
            rightPoints = FloatArray(pointCount)
        }
        leftColor = palette.colors.first()
        rightColor = palette.colors.last()
        drawFill = params.quality >= FILL_MIN_QUALITY
        drawGlow = params.quality >= GLOW_MIN_QUALITY
        if (!hasSize) return
        val w = width.toFloat()
        val h = height.toFloat()
        margin = max(8f, min(w, h) * 0.065f)
        centerY = h * 0.5f
        span = w - margin * 2f
        halfHeight = (h - margin * 2f) * 0.5f
        val base = max(1.5f, min(w, h) * 0.004f)
        mainPaint.strokeWidth = base * 1.5f
        glowPaint.strokeWidth = base * 6f

        val grid = Argb.lerp(leftColor, 0xFF50C8C8.toInt(), 0.5f)
        gridPaint.strokeWidth = 1f
        gridPaint.color = Argb.withAlpha(grid, 0.11f)
        axisPaint.strokeWidth = max(1f, base * 0.5f)
        axisPaint.color = Argb.withAlpha(grid, 0.32f)
        gridPath.rewind()
        val top = margin
        val bottom = h - margin
        for (i in 0..GRID_X) {
            val x = margin + span * i / GRID_X
            gridPath.moveTo(x, top)
            gridPath.lineTo(x, bottom)
        }
        for (j in 0..GRID_Y) {
            val y = top + (bottom - top) * j / GRID_Y
            gridPath.moveTo(margin, y)
            gridPath.lineTo(w - margin, y)
        }
        axisPath.rewind()
        axisPath.moveTo(margin, centerY)
        axisPath.lineTo(w - margin, centerY)
        axisPath.moveTo(w * 0.5f, top)
        axisPath.lineTo(w * 0.5f, bottom)
        val tick = max(2f, halfHeight * 0.03f)
        for (i in 0..GRID_X * 5) {
            val x = margin + span * i / (GRID_X * 5)
            axisPath.moveTo(x, centerY - tick)
            axisPath.lineTo(x, centerY + tick)
        }
        val m = margin * 0.35f
        triggerPath.rewind()
        triggerPath.moveTo(margin - m * 1.6f, centerY - m * 0.7f)
        triggerPath.lineTo(margin - m * 0.4f, centerY)
        triggerPath.lineTo(margin - m * 1.6f, centerY + m * 0.7f)
        triggerPath.close()
        markerPaint.color = leftColor

        leftFill.fillShader = fadeToAxis(leftColor, top, bottom)
        rightFill.fillShader = fadeToAxis(rightColor, top, bottom)

        drawLabels = width >= 300 && height >= 180
        val face: Typeface? = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        labelPaint.typeface = face
        labelPaint.textSize = (h * 0.032f).coerceIn(10f, 18f)
    }

    private fun fadeToAxis(color: Int, top: Float, bottom: Float): Shader = LinearGradient(
        0f, top, 0f, bottom,
        intArrayOf(Argb.withAlpha(color, 0.55f), Argb.TRANSPARENT, Argb.withAlpha(color, 0.55f)),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )

    override fun update(frame: AudioFrame, deltaSeconds: Float) {
        val left = frame.waveformLeft
        val right = frame.waveformRight
        val n = left.size
        if (n < 4 || right.size < 4) return
        // Disparo: primer cruce por cero ascendente en la primera mitad del buffer.
        val window = n / 2
        var start = 0
        for (i in 0 until window - 1) {
            if (left[i] <= 0f && left[i + 1] > 0f) {
                start = i
                break
            }
        }
        val k = smoothing(deltaSeconds, 45f * params.speed)
        for (p in 0 until pointCount) {
            val pos = start + p.toFloat() / (pointCount - 1) * (window - 1)
            leftPoints[p] += (sampleLinear(left, pos) - leftPoints[p]) * k
            rightPoints[p] += (sampleLinear(right, pos) - rightPoints[p]) * k
        }
        level += (frame.rms - level) * smoothing(deltaSeconds, 8f)
        flash = max(flash * exp(-deltaSeconds * 6f), frame.onset)
    }

    override fun draw(canvas: Canvas) {
        if (!hasSize || span <= 0f) return
        val amplitude = halfHeight * 0.86f * params.sensitivity * params.intensity.coerceIn(0.1f, 2f)

        canvas.drawPath(gridPath, gridPaint)
        axisPaint.alpha = alpha255(0.26f + 0.2f * level)
        canvas.drawPath(axisPath, axisPaint)
        canvas.drawPath(triggerPath, markerPaint)

        buildPath(leftPath, leftPoints, amplitude)
        buildPath(rightPath, rightPoints, amplitude)

        if (drawFill) {
            fill(canvas, rightPoints, amplitude, rightFill)
            fill(canvas, leftPoints, amplitude, leftFill)
        }
        if (drawGlow) {
            val glowAlpha = alpha255((0.2f + 0.3f * flash).coerceAtMost(0.5f))
            glowPaint.color = rightColor
            glowPaint.alpha = glowAlpha
            canvas.drawPath(rightPath, glowPaint)
            glowPaint.color = leftColor
            glowPaint.alpha = glowAlpha
            canvas.drawPath(leftPath, glowPaint)
        }
        mainPaint.color = rightColor
        canvas.drawPath(rightPath, mainPaint)
        mainPaint.color = leftColor
        canvas.drawPath(leftPath, mainPaint)

        if (drawLabels) {
            val y = margin + labelPaint.textSize * 1.4f
            labelPaint.color = leftColor
            canvas.drawText("L", margin + labelPaint.textSize * 0.6f, y, labelPaint)
            labelPaint.color = rightColor
            canvas.drawText("R", margin + labelPaint.textSize * 1.8f, y, labelPaint)
        }
    }

    /** Rellena entre la traza y el eje (degradado que se desvanece hacia el centro). */
    private fun fill(canvas: Canvas, points: FloatArray, amplitude: Float, paint: Paint) {
        fillPath.rewind()
        fillPath.moveTo(margin, centerY)
        val last = points.size - 1
        for (i in points.indices) {
            fillPath.lineTo(margin + span * i / last, centerY - points[i].coerceIn(-1f, 1f) * amplitude)
        }
        fillPath.lineTo(margin + span, centerY)
        fillPath.close()
        canvas.drawPath(fillPath, paint)
    }

    private fun buildPath(path: Path, points: FloatArray, amplitude: Float) {
        path.rewind()
        val last = points.size - 1
        for (i in points.indices) {
            val x = margin + span * i / last
            val y = centerY - points[i].coerceIn(-1f, 1f) * amplitude
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
    }

    companion object {
        const val ID = "oscilloscope"
        const val FILL_MIN_QUALITY = 0.6f
        const val GLOW_MIN_QUALITY = 0.4f
        private const val GRID_X = 10
        private const val GRID_Y = 6
    }
}
