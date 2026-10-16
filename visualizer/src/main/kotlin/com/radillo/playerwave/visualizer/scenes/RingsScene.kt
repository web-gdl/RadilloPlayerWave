package com.radillo.playerwave.visualizer.scenes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.radillo.playerwave.visualizer.analysis.AudioFrame
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Escena C: tres anillos concéntricos (graves, medios, agudos). Cada anillo es un polígono
 * cuyo radio se deforma con las bandas de su rango (simetría espejo), respira con la energía
 * del rango y gira lentamente; los transitorios lanzan ondas expansivas y el núcleo late con el
 * nivel RMS.
 */
class RingsScene : CanvasScene(id = ID, displayName = "Anillos") {

    private class Ring(val vertexCount: Int) {
        val radii = FloatArray(vertexCount) { 1f }
        var energy = 0f
        var rotation = 0f
    }

    private val rings = arrayOf(Ring(VERTICES), Ring(VERTICES), Ring(VERTICES))
    private val path = Path()
    private val pulses = FloatArray(MAX_PULSES) { -1f } // edad en segundos; -1 = inactivo
    private var core = 0f
    private var centerX = 0f
    private var centerY = 0f
    private var baseRadius = 0f
    private var coreBrush: Brush = SolidColor(Color.Transparent)
    private var strokes = Array(3) { Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    private var glowStrokes = Array(3) { Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    private var pulseStroke = Stroke(width = 2f)
    private val bases = FloatArray(3)
    private val energies = FloatArray(3)

    override fun rebuild() {
        if (!hasSize) return
        centerX = width * 0.5f
        centerY = height * 0.5f
        baseRadius = min(width, height) * 0.5f
        bases[0] = baseRadius * 0.30f
        bases[1] = baseRadius * 0.47f
        bases[2] = baseRadius * 0.63f
        val unit = max(1.5f, baseRadius * 0.012f)
        val widths = floatArrayOf(unit * 2.6f, unit * 1.8f, unit * 1.1f)
        strokes = Array(3) { Stroke(width = widths[it], cap = StrokeCap.Round, join = StrokeJoin.Round) }
        glowStrokes = Array(3) { Stroke(width = widths[it] * 4f, cap = StrokeCap.Round, join = StrokeJoin.Round) }
        pulseStroke = Stroke(width = unit * 1.2f)
        coreBrush = Brush.radialGradient(
            colors = listOf(palette.accent.copy(alpha = 0.95f), palette.colors.first().copy(alpha = 0.35f), Color.Transparent),
            center = Offset(centerX, centerY),
            radius = baseRadius * 0.28f,
        )
    }

    override fun update(frame: AudioFrame, deltaSeconds: Float) {
        val bands = frame.bands
        val n = bands.size
        val sensitivity = params.sensitivity
        energies[0] = frame.bass
        energies[1] = frame.mid
        energies[2] = frame.treble
        val rise = smoothing(deltaSeconds, 16f * params.speed)
        val fall = smoothing(deltaSeconds, 5f * params.speed)

        for (r in rings.indices) {
            val ring = rings[r]
            val lo = n * r / 3
            val hi = n * (r + 1) / 3
            val count = max(1, hi - lo)
            val targetEnergy = (energies[r] * sensitivity).coerceIn(0f, 1f)
            ring.energy += (targetEnergy - ring.energy) * (if (targetEnergy > ring.energy) rise else fall)
            val half = ring.vertexCount / 2
            for (v in 0 until ring.vertexCount) {
                // Simetría espejo: el vértice v y el vertexCount - v leen la misma banda.
                val mirrored = if (v <= half) v else ring.vertexCount - v
                val pos = lo + mirrored.toFloat() / half * (count - 1)
                val band = (sampleLinear(bands, pos.coerceIn(lo.toFloat(), (hi - 1).toFloat())) * sensitivity).coerceIn(0f, 1f)
                val target = 1f + band * 0.22f * params.intensity
                val cur = ring.radii[v]
                ring.radii[v] = cur + (target - cur) * (if (target > cur) rise else fall)
            }
            ring.rotation += deltaSeconds * SPIN[r] * params.speed
        }

        // Pulsos: envejecen y mueren; un transitorio fuerte lanza uno nuevo.
        for (i in pulses.indices) {
            if (pulses[i] >= 0f) {
                pulses[i] += deltaSeconds
                if (pulses[i] > PULSE_LIFE) pulses[i] = -1f
            }
        }
        if (frame.onset > 0.55f && !frame.synthetic) {
            val slot = pulses.indexOfFirst { it < 0f }
            if (slot >= 0 && pulses.none { it in 0f..0.12f }) pulses[slot] = 0f
        }
        core += ((frame.rms * sensitivity).coerceIn(0f, 1f) - core) * smoothing(deltaSeconds, 14f)
    }

    override fun draw(scope: DrawScope) = with(scope) {
        if (!hasSize) return
        val intensity = params.intensity.coerceIn(0.1f, 2f)
        val colors = palette.colors

        // Núcleo.
        drawCircle(
            brush = coreBrush,
            radius = baseRadius * 0.28f * (0.75f + 0.5f * core),
            center = Offset(centerX, centerY),
            alpha = (0.5f + 0.5f * core).coerceAtMost(1f),
        )

        // Anillos, del exterior al interior para que los graves queden encima.
        for (r in rings.indices.reversed()) {
            val ring = rings[r]
            val color = colors[min(r, colors.lastIndex)]
            val radius = bases[r] * (1f + 0.18f * ring.energy * intensity)
            buildRing(ring, radius)
            drawPath(path, color = color, alpha = 0.22f + 0.25f * ring.energy, style = glowStrokes[r])
            drawPath(path, color = color, alpha = 0.75f + 0.25f * ring.energy, style = strokes[r])
        }

        // Ondas expansivas.
        for (age in pulses) {
            if (age < 0f) continue
            val t = age / PULSE_LIFE
            val radius = bases[0] * (1f + 1.4f * t)
            val alpha = (1f - t) * (1f - t) * 0.7f
            drawCircle(color = palette.accent, radius = radius, center = Offset(centerX, centerY), alpha = alpha, style = pulseStroke)
        }
    }

    private fun buildRing(ring: Ring, radius: Float) {
        path.reset()
        val n = ring.vertexCount
        val step = (2.0 * PI / n).toFloat()
        for (v in 0 until n) {
            val angle = ring.rotation + v * step
            val rad = radius * ring.radii[v]
            val x = centerX + cos(angle) * rad
            val y = centerY + sin(angle) * rad
            if (v == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    companion object {
        const val ID = "rings"
        private const val VERTICES = 120
        private const val MAX_PULSES = 4
        private const val PULSE_LIFE = 1.1f
        private val SPIN = floatArrayOf(0.18f, -0.12f, 0.26f)
    }
}
