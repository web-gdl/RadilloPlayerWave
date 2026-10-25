package com.radillo.playerwave.visualizer.scenes

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.Shader
import com.radillo.playerwave.visualizer.analysis.AudioFrame
import com.radillo.playerwave.visualizer.engine.Argb
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

    private val barPaint = fillPaint()
    private val glowPaint = fillPaint()
    private val haloPaint = fillPaint()
    private val capPaint = fillPaint()

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
        capPaint.color = palette.accent
        if (!hasSize) return
        val w = width.toFloat()
        val h = height.toFloat()
        baseline = h * 0.90f
        maxBarHeight = h * 0.74f
        gap = max(2f, w * 0.0045f)
        leftPadding = w * 0.04f
        barWidth = ((w - leftPadding * 2f) - gap * (barCount - 1)) / barCount
        val colors = palette.colors
        // De agudo (arriba) a grave (abajo): el degradado va de la cima de la barra a la base.
        val vertical = IntArray(colors.size) { colors[colors.size - 1 - it] }
        barPaint.fillShader = LinearGradient(0f, baseline - maxBarHeight, 0f, baseline, vertical, null, Shader.TileMode.CLAMP)
        glowPaint.fillShader = LinearGradient(
            0f, baseline - maxBarHeight, 0f, baseline,
            intArrayOf(Argb.withAlpha(colors.last(), 0.55f), Argb.withAlpha(colors.first(), 0f)),
            null, Shader.TileMode.CLAMP,
        )
        haloPaint.fillShader = RadialGradient(
            w * 0.5f, baseline, w * 0.65f,
            intArrayOf(Argb.withAlpha(colors.first(), 0.55f), Argb.TRANSPARENT),
            null, Shader.TileMode.CLAMP,
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

    override fun draw(canvas: Canvas) {
        if (!hasSize || barWidth <= 0f) return
        val intensity = params.intensity.coerceIn(0.1f, 2f)
        val w = width.toFloat()

        // Halo de graves tras las barras.
        haloPaint.alpha = alpha255((0.25f + 0.55f * bassGlow) * intensity.coerceAtMost(1f))
        canvas.drawCircle(w * 0.5f, baseline, w * (0.35f + 0.45f * bassGlow), haloPaint)

        val minHeight = barWidth * 0.5f
        glowPaint.alpha = alpha255((0.16f + 0.45f * flash) * intensity.coerceAtMost(1f))
        capPaint.alpha = alpha255(0.85f)
        val radius = barWidth * 0.5f
        val capHeight = max(2f, barWidth * 0.22f)
        for (i in 0 until barCount) {
            val x = leftPadding + i * (barWidth + gap)
            val barHeight = max(minHeight, levels[i] * maxBarHeight * intensity)
            val top = baseline - barHeight

            // Resplandor (más ancho, translúcido).
            canvas.drawRoundRect(
                x - barWidth * 0.45f, top - barWidth * 0.6f,
                x + barWidth * 1.45f, baseline + barWidth * 0.6f,
                radius, radius, glowPaint,
            )
            // Barra.
            canvas.drawRoundRect(x, top, x + barWidth, baseline, radius, radius, barPaint)
            // Capuchón de pico.
            val capTop = baseline - max(minHeight, peaks[i] * maxBarHeight * intensity) - capHeight * 1.6f
            canvas.drawRoundRect(x, capTop, x + barWidth, capTop + capHeight, radius, radius, capPaint)
        }
    }

    companion object {
        const val ID = "spectrum"
    }
}
