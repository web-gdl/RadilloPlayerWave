package com.radillo.playerwave.visualizer.scenes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import com.radillo.playerwave.visualizer.analysis.AudioFrame
import com.radillo.playerwave.visualizer.engine.Argb
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Escena H: ambiente. Manchas de luz suaves (degradados radiales sumados) derivan por la pantalla
 * en trayectorias lentas; cada una escucha un tramo del espectro y respira con él. Es la escena
 * más tranquila y la menos costosa: pensada para dejarla de fondo y para cuando los datos de
 * audio son de respaldo, porque el movimiento sintético también la hace vivir.
 *
 * La mezcla aditiva (`PorterDuff.ADD`) se limita al radio de cada mancha; el resto del fondo no
 * se toca, así que la tasa de relleno sigue siendo baja en GPUs modestas.
 */
class AmbientScene : CanvasScene(id = ID, displayName = "Ambiente") {

    private var blobCount = 0
    private var energy = FloatArray(0)
    private var radii = FloatArray(0)
    private var blobPaints = Array(0) { Paint() }
    private val haloPaint = fillPaint().apply { setXfermode(PorterDuffXfermode(PorterDuff.Mode.ADD)) }
    private val vignettePaint = fillPaint()
    private var haloRadius = 0f
    private var centerX = 0f
    private var centerY = 0f

    private var time = 0f
    private var rms = 0f
    private var flash = 0f

    override fun rebuild() {
        val newCount = (5f * params.effectiveDensity).roundToInt().coerceIn(3, 9)
        if (newCount != blobCount) {
            blobCount = newCount
            energy = FloatArray(blobCount)
            blobPaints = Array(blobCount) { fillPaint().apply { setXfermode(PorterDuffXfermode(PorterDuff.Mode.ADD)) } }
        }
        if (!hasSize) return
        centerX = width * 0.5f
        centerY = height * 0.5f
        val base = min(width, height).toFloat()
        val colors = palette.colors
        radii = FloatArray(blobCount) { i -> base * (0.30f + 0.22f * ((i * 7) % 5) / 4f) }
        for (i in 0 until blobCount) {
            val color = colors[i % colors.size]
            blobPaints[i].fillShader = RadialGradient(
                0f, 0f, radii[i],
                intArrayOf(Argb.withAlpha(color, 0.85f), Argb.withAlpha(color, 0.28f), Argb.TRANSPARENT),
                null, Shader.TileMode.CLAMP,
            )
        }
        haloRadius = base * 0.5f
        haloPaint.fillShader = RadialGradient(
            centerX, centerY, haloRadius,
            intArrayOf(Argb.withAlpha(palette.accent, 0.5f), Argb.TRANSPARENT),
            null, Shader.TileMode.CLAMP,
        )
        vignettePaint.fillShader = RadialGradient(
            centerX, centerY, max(width, height) * 0.72f,
            intArrayOf(Argb.TRANSPARENT, Argb.TRANSPARENT, Argb.withAlpha(palette.background, 0.75f)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
    }

    override fun update(frame: AudioFrame, deltaSeconds: Float) {
        if (!hasSize) return
        val s = params.sensitivity
        time += deltaSeconds * 0.25f * params.speed
        val rise = smoothing(deltaSeconds, 3.5f)
        val fall = smoothing(deltaSeconds, 1.6f)
        val bands = frame.bands
        val n = bands.size
        for (i in 0 until blobCount) {
            val lo = n * i / blobCount
            val hi = max(lo + 1, n * (i + 1) / blobCount).coerceAtMost(n)
            if (lo >= hi) {
                energy[i] = 0f
                continue
            }
            var sum = 0f
            for (b in lo until hi) sum += bands[b]
            val target = (sum / (hi - lo) * s).coerceIn(0f, 1f)
            val cur = energy[i]
            energy[i] = cur + (target - cur) * (if (target > cur) rise else fall)
        }
        val targetRms = (frame.rms * s).coerceIn(0f, 1f)
        rms += (targetRms - rms) * (if (targetRms > rms) rise else fall)
        flash = max(flash * exp(-deltaSeconds * 2.5f), if (frame.synthetic) 0f else frame.onset * 0.8f)
    }

    override fun draw(canvas: Canvas) {
        if (!hasSize) return
        val intensity = params.intensity.coerceIn(0.1f, 2f)
        val w = width.toFloat()
        val h = height.toFloat()
        for (i in 0 until blobCount) {
            val f1 = 0.6f + 0.17f * i
            val f2 = 0.45f + 0.13f * ((i * 3) % blobCount)
            val px = centerX + sin(time * f1 + i * 1.7f) * w * 0.32f
            val py = centerY + cos(time * f2 + i * 0.9f) * h * 0.30f
            val e = energy[i]
            val scale = 0.75f + 0.6f * e * intensity
            val paint = blobPaints[i]
            paint.alpha = alpha255(((0.20f + 0.5f * e) * (0.6f + 0.4f * rms)).coerceIn(0f, 1f))
            canvas.save()
            canvas.translate(px, py)
            canvas.scale(scale, scale)
            canvas.drawCircle(0f, 0f, radii[i], paint)
            canvas.restore()
        }
        if (flash > 0.02f) {
            haloPaint.alpha = alpha255(flash * 0.6f)
            canvas.drawCircle(centerX, centerY, haloRadius * (0.8f + 0.4f * flash), haloPaint)
        }
        canvas.drawRect(0f, 0f, w, h, vignettePaint)
    }

    companion object {
        const val ID = "ambient"
    }
}
