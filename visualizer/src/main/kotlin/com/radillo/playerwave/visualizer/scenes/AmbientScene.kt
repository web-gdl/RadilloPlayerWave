package com.radillo.playerwave.visualizer.scenes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.radillo.playerwave.visualizer.analysis.AudioFrame
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
 */
class AmbientScene : CanvasScene(id = ID, displayName = "Ambiente") {

    private var blobCount = 0
    private var energy = FloatArray(0)
    private var radii = FloatArray(0)
    private var brushes = Array<Brush>(0) { SolidColor(Color.Transparent) }
    private var vignette: Brush = SolidColor(Color.Transparent)
    private var halo: Brush = SolidColor(Color.Transparent)
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
        }
        if (!hasSize) return
        centerX = width * 0.5f
        centerY = height * 0.5f
        val base = min(width, height).toFloat()
        val colors = palette.colors
        radii = FloatArray(blobCount) { i -> base * (0.30f + 0.22f * ((i * 7) % 5) / 4f) }
        brushes = Array(blobCount) { i ->
            val color = colors[i % colors.size]
            Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.85f), color.copy(alpha = 0.28f), Color.Transparent),
                center = Offset.Zero,
                radius = radii[i],
            )
        }
        haloRadius = base * 0.5f
        halo = Brush.radialGradient(
            colors = listOf(palette.accent.copy(alpha = 0.5f), Color.Transparent),
            center = Offset(centerX, centerY),
            radius = haloRadius,
        )
        vignette = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Transparent, palette.background.copy(alpha = 0.75f)),
            center = Offset(centerX, centerY),
            radius = max(width, height) * 0.72f,
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

    override fun draw(scope: DrawScope) = with(scope) {
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
            val alpha = ((0.20f + 0.5f * e) * (0.6f + 0.4f * rms)).coerceIn(0f, 1f)
            withTransform({
                translate(px, py)
                scale(scale, scale, Offset.Zero)
            }) {
                drawCircle(
                    brush = brushes[i],
                    radius = radii[i],
                    center = Offset.Zero,
                    alpha = alpha,
                    blendMode = BlendMode.Plus,
                )
            }
        }
        if (flash > 0.02f) {
            drawCircle(
                brush = halo,
                radius = haloRadius * (0.8f + 0.4f * flash),
                center = Offset(centerX, centerY),
                alpha = flash * 0.6f,
                blendMode = BlendMode.Plus,
            )
        }
        drawRect(brush = vignette)
    }

    companion object {
        const val ID = "ambient"
    }
}
