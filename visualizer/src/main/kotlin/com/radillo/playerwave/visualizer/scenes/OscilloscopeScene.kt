package com.radillo.playerwave.visualizer.scenes

import android.graphics.Canvas
import android.graphics.Path
import com.radillo.playerwave.visualizer.analysis.AudioFrame
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Escena B: osciloscopio estéreo de doble traza. El canal izquierdo se dibuja arriba y el
 * derecho abajo, con disparo por cruce por cero para que la onda no "corra", resplandor
 * suave y una línea base que respira con el nivel RMS.
 */
class OscilloscopeScene : CanvasScene(id = ID, displayName = "Osciloscopio") {

    private var pointCount = 240
    private var leftPoints = FloatArray(pointCount)
    private var rightPoints = FloatArray(pointCount)
    private val leftPath = Path()
    private val rightPath = Path()

    private val mainPaint = strokePaint(3f)
    private val glowPaint = strokePaint(12f)
    private val hairPaint = strokePaint(1f, rounded = false)

    private var leftColor = 0
    private var rightColor = 0
    private var level = 0f
    private var flash = 0f

    override fun rebuild() {
        val newCount = (240f * params.effectiveDensity).roundToInt().coerceIn(64, 512)
        if (newCount != pointCount || leftPoints.size != newCount) {
            pointCount = newCount
            leftPoints = FloatArray(pointCount)
            rightPoints = FloatArray(pointCount)
        }
        leftColor = palette.colors.first()
        rightColor = palette.colors.last()
        if (!hasSize) return
        val base = max(1.5f, minOf(width, height) * 0.004f)
        mainPaint.strokeWidth = base * 1.6f
        glowPaint.strokeWidth = base * 7f
        hairPaint.strokeWidth = max(1f, base * 0.5f)
    }

    override fun update(frame: AudioFrame, deltaSeconds: Float) {
        val left = frame.waveformLeft
        val right = frame.waveformRight
        val n = left.size
        if (n < 4) return
        // Disparo: primer cruce por cero ascendente en la primera mitad del buffer.
        val span = n / 2
        var start = 0
        for (i in 0 until span - 1) {
            if (left[i] <= 0f && left[i + 1] > 0f) {
                start = i
                break
            }
        }
        val k = smoothing(deltaSeconds, 45f * params.speed)
        for (p in 0 until pointCount) {
            val pos = start + p.toFloat() / (pointCount - 1) * (span - 1)
            leftPoints[p] += (sampleLinear(left, pos) - leftPoints[p]) * k
            rightPoints[p] += (sampleLinear(right, pos) - rightPoints[p]) * k
        }
        level += (frame.rms - level) * smoothing(deltaSeconds, 8f)
        flash = max(flash * exp(-deltaSeconds * 6f), frame.onset)
    }

    override fun draw(canvas: Canvas) {
        if (!hasSize) return
        val w = width.toFloat()
        val h = height.toFloat()
        val amplitude = h * 0.20f * params.sensitivity * params.intensity.coerceIn(0.1f, 2f)
        val leftCenter = h * 0.33f
        val rightCenter = h * 0.67f
        val margin = w * 0.04f
        val span = w - margin * 2f

        // Líneas base.
        val baseAlpha = alpha255(0.10f + 0.12f * level)
        hairPaint.color = leftColor
        hairPaint.alpha = baseAlpha
        canvas.drawLine(margin, leftCenter, w - margin, leftCenter, hairPaint)
        hairPaint.color = rightColor
        hairPaint.alpha = baseAlpha
        canvas.drawLine(margin, rightCenter, w - margin, rightCenter, hairPaint)

        buildPath(leftPath, leftPoints, margin, span, leftCenter, amplitude)
        buildPath(rightPath, rightPoints, margin, span, rightCenter, amplitude)

        val glowAlpha = alpha255((0.18f + 0.35f * flash).coerceAtMost(0.6f))
        glowPaint.color = leftColor
        glowPaint.alpha = glowAlpha
        canvas.drawPath(leftPath, glowPaint)
        glowPaint.color = rightColor
        glowPaint.alpha = glowAlpha
        canvas.drawPath(rightPath, glowPaint)
        mainPaint.color = leftColor
        canvas.drawPath(leftPath, mainPaint)
        mainPaint.color = rightColor
        canvas.drawPath(rightPath, mainPaint)
    }

    private fun buildPath(path: Path, points: FloatArray, startX: Float, span: Float, centerY: Float, amplitude: Float) {
        path.rewind()
        val last = points.size - 1
        for (i in points.indices) {
            val x = startX + span * i / last
            val y = centerY - points[i].coerceIn(-1f, 1f) * amplitude
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
    }

    companion object {
        const val ID = "oscilloscope"
    }
}
