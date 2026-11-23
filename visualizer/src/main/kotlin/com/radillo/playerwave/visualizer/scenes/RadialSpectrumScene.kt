package com.radillo.playerwave.visualizer.scenes

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.RadialGradient
import android.graphics.Shader
import com.radillo.playerwave.visualizer.analysis.AudioFrame
import com.radillo.playerwave.visualizer.engine.Argb
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Escena 2: **espectro radial**. Las barras salen hacia afuera desde un anillo, en espejo
 * izquierda/derecha (graves arriba, agudos abajo), cada una con su punto de pico; dentro, un
 * núcleo que late con el nivel y un halo de graves detrás. Los golpes fuertes lanzan una onda
 * expansiva. Los colores recorren la paleta de graves a agudos.
 *
 * Con calidad baja el gobernador quita barras, después el halo y por último los resplandores.
 */
class RadialSpectrumScene : CanvasScene(id = ID, displayName = "Radial") {

    private var half = 40
    private var levels = FloatArray(half)
    private var peaks = FloatArray(half)
    private var peakVelocity = FloatArray(half)
    private var targets = FloatArray(half)
    private var colors = IntArray(half)
    private var dirX = FloatArray(half * 2)
    private var dirY = FloatArray(half * 2)

    private val barPaint = strokePaint(4f)
    private val glowPaint = strokePaint(10f)
    private val dotPaint = fillPaint()
    private val ringPaint = strokePaint(2f, rounded = false)
    private val dashPaint = strokePaint(1.5f, rounded = false)
    private val corePaint = fillPaint()
    private val haloPaint = fillPaint()
    private val pulsePaint = strokePaint(2f, rounded = false)

    private var centerX = 0f
    private var centerY = 0f
    private var innerRadius = 0f
    private var maxLength = 0f
    private var minLength = 0f
    private var dotRadius = 2f
    private var haloRadius = 0f
    private var drawGlow = true
    private var drawHalo = true

    private var bass = 0f
    private var level = 0f
    private val pulses = FloatArray(MAX_PULSES) { -1f }

    override fun rebuild() {
        val newHalf = (40f * params.effectiveDensity).roundToInt().coerceIn(16, 64)
        if (newHalf != half || levels.size != newHalf) {
            half = newHalf
            levels = FloatArray(half)
            peaks = FloatArray(half)
            peakVelocity = FloatArray(half)
            targets = FloatArray(half)
            colors = IntArray(half)
            dirX = FloatArray(half * 2)
            dirY = FloatArray(half * 2)
        }
        drawGlow = params.quality >= GLOW_MIN_QUALITY
        drawHalo = params.quality >= HALO_MIN_QUALITY
        val total = half * 2
        for (k in 0 until total) {
            val angle = -PI / 2 + 2.0 * PI * k / total
            dirX[k] = cos(angle).toFloat()
            dirY[k] = sin(angle).toFloat()
        }
        val pal = palette.colors
        for (m in 0 until half) {
            val t = if (half > 1) m.toFloat() / (half - 1) * (pal.size - 1) else 0f
            val i = t.toInt().coerceAtMost(pal.size - 2)
            colors[m] = Argb.lerp(pal[i], pal[i + 1], t - i)
        }
        pulsePaint.color = pal[pal.size / 2]
        if (!hasSize) return
        val side = min(width, height).toFloat()
        centerX = width * 0.5f
        centerY = height * 0.5f
        innerRadius = side * 0.17f
        maxLength = side * 0.22f
        minLength = side * 0.018f
        val circumference = (2.0 * PI * innerRadius).toFloat()
        val barWidth = max(1.5f, circumference / total * 0.55f)
        barPaint.strokeWidth = barWidth
        glowPaint.strokeWidth = barWidth * 2.6f
        dotRadius = max(1.2f, barWidth * 0.4f)
        dotPaint.color = Argb.withAlpha(palette.accent, 0.85f)
        ringPaint.strokeWidth = max(1.5f, side * 0.006f)
        ringPaint.color = Argb.withAlpha(Argb.lerp(palette.accent, pal[pal.size / 2], 0.4f), 0.6f)
        dashPaint.strokeWidth = max(1f, side * 0.003f)
        dashPaint.color = Argb.withAlpha(pal[pal.size / 2], 0.28f)
        dashPaint.pathEffect = DashPathEffect(floatArrayOf(side * 0.006f, side * 0.014f), 0f)
        pulsePaint.strokeWidth = max(1.5f, side * 0.004f)
        corePaint.fillShader = RadialGradient(
            centerX, centerY, innerRadius,
            intArrayOf(
                Argb.withAlpha(palette.accent, 0.95f),
                Argb.withAlpha(pal[pal.size - 2], 0.75f),
                Argb.withAlpha(pal.first(), 0.2f),
                Argb.TRANSPARENT,
            ),
            floatArrayOf(0f, 0.28f, 0.72f, 1f),
            Shader.TileMode.CLAMP,
        )
        haloRadius = side * 0.62f
        haloPaint.fillShader = RadialGradient(
            centerX, centerY, haloRadius,
            intArrayOf(Argb.withAlpha(pal[pal.size / 2], 0.42f), Argb.TRANSPARENT),
            null, Shader.TileMode.CLAMP,
        )
    }

    override fun update(frame: AudioFrame, deltaSeconds: Float) {
        val bands = frame.bands
        val n = bands.size
        val sensitivity = params.sensitivity
        for (m in 0 until half) {
            val pos = if (half > 1) m.toFloat() / (half - 1) * (n - 1) else 0f
            targets[m] = (sampleLinear(bands, pos) * sensitivity).coerceIn(0f, 1f)
        }
        val rise = smoothing(deltaSeconds, 22f * params.speed)
        val fall = smoothing(deltaSeconds, 7f * params.speed)
        val gravity = 2.2f * params.speed
        for (m in 0 until half) {
            val v = follow(levels[m], targets[m], rise, fall)
            levels[m] = v
            if (v >= peaks[m]) {
                peaks[m] = v
                peakVelocity[m] = 0f
            } else {
                peakVelocity[m] += gravity * deltaSeconds
                peaks[m] = max(v, peaks[m] - peakVelocity[m] * deltaSeconds)
            }
        }
        bass += ((frame.bass * sensitivity).coerceIn(0f, 1f) - bass) * smoothing(deltaSeconds, 12f)
        level += ((frame.rms * sensitivity).coerceIn(0f, 1f) - level) * smoothing(deltaSeconds, 14f)
        for (i in pulses.indices) {
            if (pulses[i] >= 0f) {
                pulses[i] += deltaSeconds
                if (pulses[i] > PULSE_LIFE) pulses[i] = -1f
            }
        }
        if (frame.onset > 0.55f && !frame.synthetic) {
            val slot = pulses.indexOfFirst { it < 0f }
            if (slot >= 0 && pulses.none { it in 0f..0.15f }) pulses[slot] = 0f
        }
    }

    override fun draw(canvas: Canvas) {
        if (!hasSize || innerRadius <= 0f) return
        val intensity = params.intensity.coerceIn(0.1f, 2f)

        if (drawHalo) {
            // El círculo mide lo mismo que el degradado: así el halo se desvanece sin borde.
            haloPaint.alpha = alpha255(0.35f + 0.65f * bass)
            canvas.drawCircle(centerX, centerY, haloRadius, haloPaint)
        }
        canvas.drawCircle(centerX, centerY, innerRadius * 0.78f, dashPaint)

        val total = half * 2
        for (k in 0 until total) {
            val m = if (k < half) k else total - 1 - k
            val length = (minLength + levels[m] * maxLength * intensity).coerceAtMost(maxLength * 1.25f)
            val dx = dirX[k]
            val dy = dirY[k]
            val x0 = centerX + dx * innerRadius
            val y0 = centerY + dy * innerRadius
            val x1 = centerX + dx * (innerRadius + length)
            val y1 = centerY + dy * (innerRadius + length)
            val color = colors[m]
            if (drawGlow) {
                glowPaint.color = Argb.withAlpha(color, 0.16f + 0.2f * levels[m])
                canvas.drawLine(x0, y0, x1, y1, glowPaint)
            }
            barPaint.color = color
            canvas.drawLine(x0, y0, x1, y1, barPaint)
            val peakDistance = innerRadius + (minLength + peaks[m] * maxLength * intensity).coerceAtMost(maxLength * 1.25f) + dotRadius * 3f
            canvas.drawCircle(centerX + dx * peakDistance, centerY + dy * peakDistance, dotRadius, dotPaint)
        }

        canvas.drawCircle(centerX, centerY, innerRadius - ringPaint.strokeWidth * 2f, ringPaint)
        corePaint.alpha = alpha255(0.55f + 0.45f * level)
        canvas.drawCircle(centerX, centerY, innerRadius * (0.72f + 0.2f * level), corePaint)

        for (age in pulses) {
            if (age < 0f) continue
            val t = age / PULSE_LIFE
            pulsePaint.alpha = alpha255((1f - t) * (1f - t) * 0.55f)
            canvas.drawCircle(centerX, centerY, innerRadius * (1.1f + 1.9f * t), pulsePaint)
        }
    }

    companion object {
        const val ID = "radial"
        const val HALO_MIN_QUALITY = 0.7f
        const val GLOW_MIN_QUALITY = 0.45f
        private const val MAX_PULSES = 3
        private const val PULSE_LIFE = 1.0f
    }
}
