package com.radillo.playerwave.visualizer.scenes

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.radillo.playerwave.visualizer.analysis.AudioFrame
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Escena A: espectro de barras suaves con capuchones de pico que caen por gravedad, halo de
 * graves tras las barras y destello en los transitorios. El número de barras depende de la
 * densidad; las bandas del analizador se remuestrean linealmente.
 */
class SpectrumBarsScene : CanvasScene(id = ID, displayName = "Espectro") {

    private var barCount = 48
    private var levels = FloatArray(barCount)
    private var peaks = FloatArray(barCount)
    private var peakVelocity = FloatArray(barCount)
    private var targets = FloatArray(barCount)

    private var barBrush: Brush = SolidColor(Color.White)
    private var glowBrush: Brush = SolidColor(Color.White)
    private var haloBrush: Brush = SolidColor(Color.Transparent)
    private var capColor: Color = Color.White

    private var bassGlow = 0f
    private var flash = 0f
    private var baseline = 0f
    private var maxBarHeight = 0f
    private var barWidth = 0f
    private var gap = 0f
    private var leftPadding = 0f

    override fun rebuild() {
        val newCount = (40f * params.effectiveDensity).roundToInt().coerceIn(12, 96)
        if (newCount != barCount || levels.size != newCount) {
            barCount = newCount
            levels = FloatArray(barCount)
            peaks = FloatArray(barCount)
            peakVelocity = FloatArray(barCount)
            targets = FloatArray(barCount)
        }
        capColor = palette.accent
        if (!hasSize) return
        val w = width.toFloat()
        val h = height.toFloat()
        baseline = h * 0.90f
        maxBarHeight = h * 0.74f
        gap = max(2f, w * 0.0045f)
        leftPadding = w * 0.04f
        barWidth = ((w - leftPadding * 2f) - gap * (barCount - 1)) / barCount
        barBrush = Brush.verticalGradient(
            colors = palette.colors,
            startY = baseline - maxBarHeight,
            endY = baseline,
        )
        glowBrush = Brush.verticalGradient(
            colors = listOf(palette.colors.last().copy(alpha = 0.55f), palette.colors.first().copy(alpha = 0.0f)),
            startY = baseline - maxBarHeight,
            endY = baseline,
        )
        haloBrush = Brush.radialGradient(
            colors = listOf(palette.colors.first().copy(alpha = 0.55f), Color.Transparent),
            center = Offset(w * 0.5f, baseline),
            radius = w * 0.65f,
        )
    }

    override fun update(frame: AudioFrame, deltaSeconds: Float) {
        val bands = frame.bands
        val n = bands.size
        val sensitivity = params.sensitivity
        for (i in 0 until barCount) {
            val pos = if (barCount > 1) i.toFloat() / (barCount - 1) * (n - 1) else 0f
            targets[i] = (sampleLinear(bands, pos) * sensitivity).coerceIn(0f, 1f)
        }
        val rise = smoothing(deltaSeconds, 24f * params.speed)
        val fall = smoothing(deltaSeconds, 8f * params.speed)
        val gravity = 2.4f * params.speed
        for (i in 0 until barCount) {
            val t = targets[i]
            val v = levels[i]
            val nv = v + (t - v) * (if (t > v) rise else fall)
            levels[i] = nv
            if (nv >= peaks[i]) {
                peaks[i] = nv
                peakVelocity[i] = 0f
            } else {
                peakVelocity[i] += gravity * deltaSeconds
                peaks[i] = max(nv, peaks[i] - peakVelocity[i] * deltaSeconds)
            }
        }
        bassGlow += ((frame.bass * sensitivity).coerceIn(0f, 1f) - bassGlow) * smoothing(deltaSeconds, 10f)
        flash = max(flash * exp(-deltaSeconds * 7f), frame.onset)
    }

    override fun draw(scope: DrawScope) = with(scope) {
        if (!hasSize || barWidth <= 0f) return
        val intensity = params.intensity.coerceIn(0.1f, 2f)
        val w = size.width

        // Halo de graves tras las barras.
        drawCircle(
            brush = haloBrush,
            radius = w * (0.35f + 0.45f * bassGlow),
            center = Offset(w * 0.5f, baseline),
            alpha = (0.25f + 0.55f * bassGlow) * intensity.coerceAtMost(1f),
        )

        val minHeight = barWidth * 0.5f
        val glowAlpha = (0.16f + 0.45f * flash) * intensity.coerceAtMost(1f)
        val corner = CornerRadius(barWidth * 0.5f, barWidth * 0.5f)
        for (i in 0 until barCount) {
            val x = leftPadding + i * (barWidth + gap)
            val barHeight = max(minHeight, levels[i] * maxBarHeight * intensity)
            val top = baseline - barHeight

            // Resplandor (más ancho, translúcido).
            drawRoundRect(
                brush = glowBrush,
                topLeft = Offset(x - barWidth * 0.45f, top - barWidth * 0.6f),
                size = Size(barWidth * 1.9f, barHeight + barWidth * 1.2f),
                cornerRadius = corner,
                alpha = glowAlpha,
            )
            // Barra.
            drawRoundRect(
                brush = barBrush,
                topLeft = Offset(x, top),
                size = Size(barWidth, barHeight),
                cornerRadius = corner,
            )
            // Capuchón de pico.
            val capHeight = max(2f, barWidth * 0.22f)
            val capTop = baseline - max(minHeight, peaks[i] * maxBarHeight * intensity) - capHeight * 1.6f
            drawRoundRect(
                color = capColor,
                topLeft = Offset(x, capTop),
                size = Size(barWidth, capHeight),
                cornerRadius = corner,
                alpha = 0.85f,
            )
        }
    }

    companion object {
        const val ID = "spectrum"
    }
}
