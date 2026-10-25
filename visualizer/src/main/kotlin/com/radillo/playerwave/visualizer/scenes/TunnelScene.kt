package com.radillo.playerwave.visualizer.scenes

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.radillo.playerwave.visualizer.analysis.AudioFrame
import com.radillo.playerwave.visualizer.engine.Argb
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
 * raíles unen los anillos consecutivos para dar sensación de profundidad (un `drawLines` por
 * anillo); los transitorios encienden el fondo del túnel.
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
    private val railBuffer = FloatArray(VERTICES / RAIL_STEP * 4)
    private val vertexCos = polygonCosTable(VERTICES)
    private val vertexSin = polygonSinTable(VERTICES)

    private var centerX = 0f
    private var centerY = 0f
    private var baseRadius = 0f
    private var strokeUnit = 1.5f
    private var glowRadius = 0f
    private val glowPaint = fillPaint()
    private val ringPaint = strokePaint(2f)
    private val haloPaint = strokePaint(6f)
    private val railPaint = strokePaint(1f, rounded = false)

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
        railPaint.strokeWidth = strokeUnit * 0.6f
        glowRadius = baseRadius * 0.4f
        glowPaint.fillShader = RadialGradient(
            0f, 0f, glowRadius,
            intArrayOf(Argb.withAlpha(palette.accent, 0.9f), Argb.withAlpha(palette.colors.last(), 0.35f), Argb.TRANSPARENT),
            null, Shader.TileMode.CLAMP,
        )
    }

    override fun update(frame: AudioFrame, deltaSeconds: Float) {
        if (!hasSize) return
        val s = params.sensitivity
        val rise = smoothing(deltaSeconds, 10f)
        val fall = smoothing(deltaSeconds, 3f)
        val targetRms = (frame.rms * s).coerceIn(0f, 1f)
        rms = follow(rms, targetRms, rise, fall)
        val targetBass = (frame.bass * s).coerceIn(0f, 1f)
        bass = follow(bass, targetBass, rise, fall)
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
            colorIndex[nearest] = colorIndex[farthest % ringCount] + 1
            farthest = nearest
            nearest = (farthest + ringCount - 1) % ringCount
            guard++
        }
        driftT += deltaSeconds * 0.35f * params.speed
    }

    override fun draw(canvas: Canvas) {
        if (!hasSize) return
        val colors = palette.colors
        val intensity = params.intensity.coerceIn(0.1f, 2f)
        val vanishX = centerX + sin(driftT * 0.9f) * width * 0.05f
        val vanishY = centerY + cos(driftT * 0.7f) * height * 0.05f

        canvas.save()
        canvas.translate(vanishX, vanishY)
        glowPaint.alpha = alpha255((0.45f + 0.55f * flash).coerceAtMost(1f))
        canvas.drawCircle(0f, 0f, glowRadius * (0.6f + 0.5f * bass + 0.6f * flash), glowPaint)
        canvas.restore()

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

            val cosR = cos(rotation)
            val sinR = sin(rotation)
            path.rewind()
            for (v in 0 until VERTICES) {
                // cos/sin(rotation + v·paso) por la identidad del ángulo suma (ver polygonCosTable).
                val ca = vertexCos[v]
                val sa = vertexSin[v]
                val rad = radius * (1f + deform[v] * 0.28f * intensity)
                val px = ringCx + (cosR * ca - sinR * sa) * rad
                val py = ringCy + (sinR * ca + cosR * sa) * rad
                curX[v] = px
                curY[v] = py
                if (v == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()

            val strokeWidth = strokeUnit * (0.6f + 2.6f * depth)
            if (depth > 0.45f) {
                haloPaint.strokeWidth = strokeWidth * 3f
                haloPaint.color = color
                haloPaint.alpha = alpha255(alpha * 0.25f)
                canvas.drawPath(path, haloPaint)
            }
            ringPaint.strokeWidth = strokeWidth
            ringPaint.color = color
            ringPaint.alpha = alpha255(alpha)
            canvas.drawPath(path, ringPaint)

            if (hasPrev) {
                var v = 0
                var o = 0
                while (v < VERTICES) {
                    railBuffer[o] = prevX[v]
                    railBuffer[o + 1] = prevY[v]
                    railBuffer[o + 2] = curX[v]
                    railBuffer[o + 3] = curY[v]
                    o += 4
                    v += RAIL_STEP
                }
                railPaint.color = color
                railPaint.alpha = alpha255(alpha * 0.35f)
                canvas.drawLines(railBuffer, 0, o, railPaint)
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
        private const val Z_NEAR = 0.05f
        private const val Z_FAR = 1.0f
        private const val Z_SCALE = 0.07f
    }
}
