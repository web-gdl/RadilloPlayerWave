package com.radillo.playerwave.visualizer.scenes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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

    private var mainStroke = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private var glowStroke = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private var hairline = Stroke(width = 1f)

    private var leftColor = Color.White
    private var rightColor = Color.White
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
        rightColor = palette.colors[palette.colors.lastIndex]
        if (!hasSize) return
        val base = max(1.5f, minOf(width, height) * 0.004f)
        mainStroke = Stroke(width = base * 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        glowStroke = Stroke(width = base * 7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        hairline = Stroke(width = max(1f, base * 0.5f))
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

    override fun draw(scope: DrawScope) = with(scope) {
        if (!hasSize) return
        val w = size.width
        val h = size.height
        val amplitude = h * 0.20f * params.sensitivity * params.intensity.coerceIn(0.1f, 2f)
        val leftCenter = h * 0.33f
        val rightCenter = h * 0.67f
        val margin = w * 0.04f
        val span = w - margin * 2f

        // Líneas base.
        val baseAlpha = 0.10f + 0.12f * level
        drawLine(leftColor, Offset(margin, leftCenter), Offset(w - margin, leftCenter), strokeWidth = hairline.width, alpha = baseAlpha)
        drawLine(rightColor, Offset(margin, rightCenter), Offset(w - margin, rightCenter), strokeWidth = hairline.width, alpha = baseAlpha)

        buildPath(leftPath, leftPoints, margin, span, leftCenter, amplitude)
        buildPath(rightPath, rightPoints, margin, span, rightCenter, amplitude)

        val glowAlpha = (0.18f + 0.35f * flash).coerceAtMost(0.6f)
        drawPath(leftPath, color = leftColor, alpha = glowAlpha, style = glowStroke)
        drawPath(rightPath, color = rightColor, alpha = glowAlpha, style = glowStroke)
        drawPath(leftPath, color = leftColor, style = mainStroke)
        drawPath(rightPath, color = rightColor, style = mainStroke)
    }

    private fun buildPath(path: Path, points: FloatArray, startX: Float, span: Float, centerY: Float, amplitude: Float) {
        path.reset()
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
