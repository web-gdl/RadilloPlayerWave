package com.radillo.playerwave.visualizer.scenes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.radillo.playerwave.visualizer.analysis.AudioFrame
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Escena D: polvo luminoso. Las partículas nacen en el centro con una frecuencia asignada al
 * azar (que fija su color y qué banda las hace crecer), los graves las empujan hacia fuera, los
 * medios las hacen girar en remolino y los agudos las agitan. Los transitorios lanzan ráfagas.
 *
 * Todo el estado vive en arrays planos preasignados; `update` y `draw` no asignan memoria.
 */
class ParticlesScene : CanvasScene(id = ID, displayName = "Partículas") {

    private var capacity = 0
    private var x = FloatArray(0)
    private var y = FloatArray(0)
    private var vx = FloatArray(0)
    private var vy = FloatArray(0)
    private var life = FloatArray(0)
    private var maxLife = FloatArray(0)
    private var size = FloatArray(0)
    private var hue = FloatArray(0)
    private var colorIndex = IntArray(0)
    private var cursor = 0

    private val random = Random(7)
    private val bandCache = FloatArray(BAND_SLOTS)
    private var emitAccumulator = 0f

    private var centerX = 0f
    private var centerY = 0f
    private var unit = 1f
    private var margin = 0f
    private var glowRadius = 0f
    private var glowBrush: Brush = SolidColor(Color.Transparent)

    private var bass = 0f
    private var mid = 0f
    private var treble = 0f
    private var rms = 0f
    private var flash = 0f

    override fun rebuild() {
        val newCapacity = (260f * params.effectiveDensity).roundToInt().coerceIn(40, 800)
        if (newCapacity != capacity) {
            capacity = newCapacity
            x = FloatArray(capacity)
            y = FloatArray(capacity)
            vx = FloatArray(capacity)
            vy = FloatArray(capacity)
            life = FloatArray(capacity)
            maxLife = FloatArray(capacity)
            size = FloatArray(capacity)
            hue = FloatArray(capacity)
            colorIndex = IntArray(capacity)
            cursor = 0
        }
        if (!hasSize) return
        centerX = width * 0.5f
        centerY = height * 0.5f
        unit = min(width, height) / 720f
        margin = 40f * unit
        glowRadius = min(width, height) * 0.22f
        glowBrush = Brush.radialGradient(
            colors = listOf(palette.accent.copy(alpha = 0.55f), palette.colors.first().copy(alpha = 0.25f), Color.Transparent),
            center = Offset(centerX, centerY),
            radius = glowRadius,
        )
    }

    override fun update(frame: AudioFrame, deltaSeconds: Float) {
        if (!hasSize) return
        val s = params.sensitivity
        val rise = smoothing(deltaSeconds, 14f)
        val fall = smoothing(deltaSeconds, 4f)
        bass = follow(bass, (frame.bass * s).coerceIn(0f, 1f), rise, fall)
        mid = follow(mid, (frame.mid * s).coerceIn(0f, 1f), rise, fall)
        treble = follow(treble, (frame.treble * s).coerceIn(0f, 1f), rise, fall)
        rms = follow(rms, (frame.rms * s).coerceIn(0f, 1f), rise, fall)
        flash = max(flash * exp(-deltaSeconds * 6f), if (frame.synthetic) 0f else frame.onset)

        val bands = frame.bands
        val last = (bands.size - 1).toFloat()
        for (i in 0 until BAND_SLOTS) {
            bandCache[i] = (sampleLinear(bands, i / (BAND_SLOTS - 1f) * last) * s).coerceIn(0f, 1f)
        }

        // Emisión: la población objetivo sigue a la energía; los transitorios añaden ráfagas.
        val rate = capacity / AVERAGE_LIFE * (0.12f + 0.88f * rms)
        emitAccumulator += rate * deltaSeconds
        if (frame.onset > 0.5f && !frame.synthetic) emitAccumulator += capacity * 0.10f * frame.onset
        var toEmit = emitAccumulator.toInt()
        if (toEmit > 0) {
            toEmit = min(toEmit, capacity / 4)
            emitAccumulator -= toEmit
            repeat(toEmit) { emit() }
        }

        val speedMul = params.speed
        val intensity = params.intensity
        val push = (30f + 380f * bass) * unit * speedMul * intensity
        val swirl = (25f + 150f * mid) * unit * speedMul
        val jitter = 260f * treble * unit
        val drag = exp(-deltaSeconds * 0.9f)
        val w = width.toFloat()
        val h = height.toFloat()

        for (i in 0 until capacity) {
            if (life[i] <= 0f) continue
            life[i] -= deltaSeconds
            if (life[i] <= 0f) continue
            val dx = x[i] - centerX
            val dy = y[i] - centerY
            val dist = sqrt(dx * dx + dy * dy) + 1e-3f
            val nx = dx / dist
            val ny = dy / dist
            vx[i] += (nx * push - ny * swirl + (random.nextFloat() - 0.5f) * jitter) * deltaSeconds
            vy[i] += (ny * push + nx * swirl + (random.nextFloat() - 0.5f) * jitter) * deltaSeconds
            vx[i] *= drag
            vy[i] *= drag
            x[i] += vx[i] * deltaSeconds
            y[i] += vy[i] * deltaSeconds
            if (x[i] < -margin || x[i] > w + margin || y[i] < -margin || y[i] > h + margin) life[i] = 0f
        }
    }

    private fun emit() {
        var slot = -1
        for (attempt in 0 until capacity) {
            val candidate = (cursor + attempt) % capacity
            if (life[candidate] <= 0f) {
                slot = candidate
                break
            }
        }
        if (slot < 0) return
        cursor = (slot + 1) % capacity
        val angle = random.nextFloat() * (2f * PI.toFloat())
        val speed0 = (50f + 280f * bass) * unit * params.speed * (0.5f + random.nextFloat())
        val r0 = unit * (8f + 28f * random.nextFloat())
        x[slot] = centerX + cos(angle) * r0
        y[slot] = centerY + sin(angle) * r0
        vx[slot] = cos(angle) * speed0
        vy[slot] = sin(angle) * speed0
        val l = AVERAGE_LIFE * (0.6f + 0.8f * random.nextFloat())
        life[slot] = l
        maxLife[slot] = l
        size[slot] = unit * (1.8f + 3.6f * random.nextFloat())
        val hu = random.nextFloat()
        hue[slot] = hu
        colorIndex[slot] = (hu * (palette.colors.size - 1)).roundToInt().coerceIn(0, palette.colors.size - 1)
    }

    override fun draw(scope: DrawScope) = with(scope) {
        if (!hasSize) return
        val colors = palette.colors
        val intensity = sqrt(params.intensity.coerceIn(0.1f, 2f))

        drawCircle(
            brush = glowBrush,
            radius = glowRadius * (0.55f + 0.7f * bass + 0.4f * flash),
            center = Offset(centerX, centerY),
            alpha = (0.30f + 0.5f * bass + 0.3f * flash).coerceAtMost(1f),
        )

        for (i in 0 until capacity) {
            val l = life[i]
            if (l <= 0f) continue
            val total = maxLife[i]
            val age = total - l
            val fadeIn = min(1f, age / 0.15f)
            val t = l / total
            val fadeOut = if (t < 0.35f) t / 0.35f else 1f
            val e = bandCache[(hue[i] * (BAND_SLOTS - 1)).toInt()]
            val alpha = (fadeIn * fadeOut * (0.5f + 0.5f * e)).coerceIn(0f, 1f)
            val radius = size[i] * (0.7f + 0.9f * e) * intensity
            val color = colors[colorIndex[i]]
            val px = x[i]
            val py = y[i]
            val k = 0.045f
            drawLine(
                color = color,
                start = Offset(px - vx[i] * k, py - vy[i] * k),
                end = Offset(px, py),
                strokeWidth = radius * 1.1f,
                cap = StrokeCap.Round,
                alpha = alpha * 0.45f,
            )
            drawCircle(color = color, radius = radius, center = Offset(px, py), alpha = alpha)
            if (flash > 0.3f && e > 0.6f) {
                drawCircle(color = palette.accent, radius = radius * 0.45f, center = Offset(px, py), alpha = alpha * flash)
            }
        }
    }

    private fun follow(current: Float, target: Float, rise: Float, fall: Float): Float =
        current + (target - current) * (if (target > current) rise else fall)

    companion object {
        const val ID = "particles"
        private const val BAND_SLOTS = 16
        private const val AVERAGE_LIFE = 2.2f
    }
}
