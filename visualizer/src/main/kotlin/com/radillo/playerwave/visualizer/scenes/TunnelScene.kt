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
import androidx.compose.ui.graphics.drawscope.translate
import com.radillo.playerwave.visualizer.analysis.AudioFrame
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Escena E: túnel. Anillos poligonales avanzan hacia la cámara desde un punto de fuga que
 * deriva lentamente; la velocidad sigue al nivel RMS, el espectro deforma el contorno de todos
 * los anillos (simetría espejo) y la torsión crece con la cercanía, formando una espiral. Ocho
 * raíles unen los anillos consecutivos para dar sensación de profundidad; los transitorios
 * encienden el fondo del túnel.
 */
class TunnelScene : CanvasScene(id = ID, displayName = "Túnel") {

    private var ringCount = 0
    private var z = FloatArray(0)
    private var twist = FloatArray(0)
    private var colorIndex = IntArray(0)
    private var farthest = 0

    private val deform = FloatArray(VERTICES)
    private val deformTarget = FloatArray(VERTICES)
    private val path = Path()
    private var prevX = FloatArray(VERTICES)
    private var prevY = FloatArray(VERTICES)
    private var curX = FloatArray(VERTICES)
    private var curY = FloatArray(VERTICES)

    private var centerX = 0f
    private var centerY = 0f
    private var baseRadius = 0f
    private var strokeUnit = 1.5f
    private var glowRadius = 0f
    private var glowBrush: Brush = SolidColor(Color.Transparent)
    // Trazos por tramos de profundidad, creados en rebuild (Stroke es inmutable).
    private var ringStrokes = Array(STROKE_BUCKETS) { Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    private var haloStrokes = Array(STROKE_BUCKETS) { Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round) }

    private var driftT = 0f
    private var rms = 0f
    private var bass = 0f
    private var flash = 0f

    override fun rebuild() {
        val newCount = (16f * params.effectiveDensity).roundToInt().coerceIn(6, 36)
        if (newCount != ringCount) {
            ringCount = newCount
            z = FloatArray(ringCount) { i -> Z_FAR - (Z_FAR - Z_NEAR) * i / ringCount }
            twist = FloatArray(ringCount)
            colorIndex = IntArray(ringCount) { it }
            farthest = 0
        }
        if (!hasSize) return
        centerX = width * 0.5f
        centerY = height * 0.5f
        baseRadius = min(width, height) * 0.5f
        strokeUnit = max(1.2f, baseRadius * 0.01f)
        ringStrokes = Array(STROKE_BUCKETS) { b ->
            Stroke(width = strokeUnit * (0.6f + 2.6f * b / (STROKE_BUCKETS - 1)), cap = StrokeCap.Round, join = StrokeJoin.Round)
        }
        haloStrokes = Array(STROKE_BUCKETS) { b -> Stroke(width = ringStrokes[b].width * 3f, cap = StrokeCap.Round, join = StrokeJoin.Round) }
        glowRadius = baseRadius * 0.4f
        glowBrush = Brush.radialGradient(
            colors = listOf(palette.accent.copy(alpha = 0.9f), palette.colors.last().copy(alpha = 0.35f), Color.Transparent),
            center = Offset.Zero,
            radius = glowRadius,
        )
    }

    override fun update(frame: AudioFrame, deltaSeconds: Float) {
        if (!hasSize) return
        val s = params.sensitivity
        val rise = smoothing(deltaSeconds, 10f)
        val fall = smoothing(deltaSeconds, 3f)
        val targetRms = (frame.rms * s).coerceIn(0f, 1f)
        rms += (targetRms - rms) * (if (targetRms > rms) rise else fall)
        val targetBass = (frame.bass * s).coerceIn(0f, 1f)
        bass += (targetBass - bass) * (if (targetBass > bass) rise else fall)
        flash = max(flash * exp(-deltaSeconds * 5f), if (frame.synthetic) 0f else frame.onset)

        // Deformación compartida, simétrica, suavizada.
        val bands = frame.bands
        val n = bands.size
        val half = VERTICES / 2
        val k = smoothing(deltaSeconds, 10f)
        for (v in 0 until VERTICES) {
            val mirrored = if (v <= half) v else VERTICES - v
            val pos = mirrored.toFloat() / half * (n - 1)
            deformTarget[v] = (sampleLinear(bands, pos) * s).coerceIn(0f, 1f)
            deform[v] += (deformTarget[v] - deform[v]) * k
        }

        // Avance hacia la cámara y reciclado del anillo más cercano.
        val speedZ = (0.22f + 0.6f * rms) * params.speed
        val spin = deltaSeconds * 0.3f * params.speed
        for (i in 0 until ringCount) {
            z[i] -= speedZ * deltaSeconds
            twist[i] += spin
        }
        var guard = 0
        var nearest = (farthest + ringCount - 1) % ringCount
        while (z[nearest] < Z_NEAR && guard < ringCount) {
            z[nearest] += Z_FAR - Z_NEAR
            colorIndex[nearest] = colorIndex[(farthest) % ringCount] + 1
            farthest = nearest
            nearest = (farthest + ringCount - 1) % ringCount
            guard++
        }
        driftT += deltaSeconds * 0.35f * params.speed
    }

    override fun draw(scope: DrawScope) = with(scope) {
        if (!hasSize) return
        val colors = palette.colors
        val intensity = params.intensity.coerceIn(0.1f, 2f)
        val vanishX = centerX + sin(driftT * 0.9f) * width * 0.05f
        val vanishY = centerY + cos(driftT * 0.7f) * height * 0.05f

        translate(vanishX, vanishY) {
            drawCircle(
                brush = glowBrush,
                radius = glowRadius * (0.6f + 0.5f * bass + 0.6f * flash),
                center = Offset.Zero,
                alpha = (0.45f + 0.55f * flash).coerceAtMost(1f),
            )
        }

        val step = (2.0 * PI / VERTICES).toFloat()
        var hasPrev = false
        for (kIdx in 0 until ringCount) {
            val idx = (farthest + kIdx) % ringCount
            val zi = z[idx]
            val depth = (1f - (zi - Z_NEAR) / (Z_FAR - Z_NEAR)).coerceIn(0f, 1f)
            val radius = baseRadius * (Z_SCALE / zi)
            val nearFade = ((zi - Z_NEAR) / 0.04f).coerceIn(0f, 1f)
            val alpha = depth.pow(0.9f) * 0.9f * nearFade
            // Los anillos lejanos se agrupan en el punto de fuga; los cercanos rodean la pantalla.
            val ringCx = vanishX + (centerX - vanishX) * depth
            val ringCy = vanishY + (centerY - vanishY) * depth
            val rotation = twist[idx] + depth * 0.8f * intensity
            val color = colors[colorIndex[idx] % colors.size]

            path.rewind()
            for (v in 0 until VERTICES) {
                val angle = rotation + v * step
                val rad = radius * (1f + deform[v] * 0.28f * intensity)
                val px = ringCx + cos(angle) * rad
                val py = ringCy + sin(angle) * rad
                curX[v] = px
                curY[v] = py
                if (v == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()

            val bucket = (depth * (STROKE_BUCKETS - 1)).roundToInt().coerceIn(0, STROKE_BUCKETS - 1)
            if (depth > 0.45f) {
                drawPath(path, color = color, alpha = alpha * 0.25f, style = haloStrokes[bucket])
            }
            drawPath(path, color = color, alpha = alpha, style = ringStrokes[bucket])

            if (hasPrev) {
                var v = 0
                while (v < VERTICES) {
                    drawLine(
                        color = color,
                        start = Offset(prevX[v], prevY[v]),
                        end = Offset(curX[v], curY[v]),
                        strokeWidth = strokeUnit * 0.6f,
                        alpha = alpha * 0.35f,
                    )
                    v += RAIL_STEP
                }
            }
            val tx = prevX; prevX = curX; curX = tx
            val ty = prevY; prevY = curY; curY = ty
            hasPrev = true
        }
    }

    companion object {
        const val ID = "tunnel"
        private const val VERTICES = 64
        private const val RAIL_STEP = 8
        private const val STROKE_BUCKETS = 8
        private const val Z_NEAR = 0.05f
        private const val Z_FAR = 1.0f
        private const val Z_SCALE = 0.07f
    }
}
