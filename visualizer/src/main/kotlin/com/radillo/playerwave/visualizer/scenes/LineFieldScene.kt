package com.radillo.playerwave.visualizer.scenes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import com.radillo.playerwave.visualizer.analysis.AudioFrame
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Escena G: campo de líneas. Una rejilla de segmentos cortos sigue un campo de flujo que ondula
 * con el tiempo; cada columna lee una banda del espectro (graves a la izquierda, agudos a la
 * derecha): cuanta más energía, más se alargan y se enderezan los segmentos y más brillan. Los
 * graves hacen rotar todo el campo y los transitorios lo estiran un instante.
 */
class LineFieldScene : CanvasScene(id = ID, displayName = "Campo de líneas") {

    private var cols = 0
    private var rows = 0
    private var angle = FloatArray(0)
    private var columnEnergy = FloatArray(0)
    private var columnColors = Array(0) { Color.White }
    private var cellW = 0f
    private var cellH = 0f
    private var unit = 1f

    private var time = 0f
    private var rotation = 0f
    private var bass = 0f
    private var flash = 0f

    override fun rebuild() {
        val newCols = (26f * sqrt(params.effectiveDensity)).roundToInt().coerceIn(8, 56)
        val newRows = if (hasSize) (newCols * height / width.toFloat()).roundToInt().coerceIn(5, 64) else 0
        if (newCols != cols || newRows != rows) {
            cols = newCols
            rows = newRows
            angle = FloatArray(cols * rows)
            columnEnergy = FloatArray(cols)
        }
        val colors = palette.colors
        columnColors = Array(cols) { c ->
            val f = if (cols == 1) 0f else c / (cols - 1f) * (colors.size - 1)
            val i = f.toInt().coerceIn(0, colors.size - 1)
            val j = min(i + 1, colors.size - 1)
            lerp(colors[i], colors[j], f - i)
        }
        if (!hasSize || rows == 0) return
        cellW = width / cols.toFloat()
        cellH = height / rows.toFloat()
        unit = min(width, height) / 720f
    }

    override fun update(frame: AudioFrame, deltaSeconds: Float) {
        if (!hasSize || rows == 0) return
        val s = params.sensitivity
        time += deltaSeconds * params.speed
        val rise = smoothing(deltaSeconds, 18f)
        val fall = smoothing(deltaSeconds, 5f)
        val bands = frame.bands
        val last = (bands.size - 1).toFloat()
        for (c in 0 until cols) {
            val pos = if (cols == 1) 0f else c / (cols - 1f) * last
            val target = (sampleLinear(bands, pos) * s).coerceIn(0f, 1f)
            val cur = columnEnergy[c]
            columnEnergy[c] = cur + (target - cur) * (if (target > cur) rise else fall)
        }
        val targetBass = (frame.bass * s).coerceIn(0f, 1f)
        bass += (targetBass - bass) * (if (targetBass > bass) rise else fall)
        rotation += deltaSeconds * (0.15f + 1.2f * bass) * params.speed
        if (rotation > TWO_PI) {
            // Mantener los ángulos acotados sin saltos visibles: se restan 2π a todos a la vez.
            rotation -= TWO_PI
            for (j in angle.indices) angle[j] -= TWO_PI
        }
        flash = max(flash * exp(-deltaSeconds * 6f), if (frame.synthetic) 0f else frame.onset)

        val k = smoothing(deltaSeconds, 8f)
        val straighten = 0.8f * params.intensity.coerceIn(0.1f, 2f)
        var i = 0
        for (r in 0 until rows) {
            val fy = (r + 0.5f) / rows
            for (c in 0 until cols) {
                val fx = (c + 0.5f) / cols
                val flow = sin(fx * 5.1f + time * 0.8f + sin(fy * 3.3f - time * 0.5f)) * 0.9f +
                    cos(fy * 4.7f - time * 0.6f + fx * 2.0f) * 0.7f
                val e = columnEnergy[c]
                val target = rotation + flow * (1f - 0.7f * e) - HALF_PI * e * straighten
                angle[i] += (target - angle[i]) * k
                i++
            }
        }
    }

    override fun draw(scope: DrawScope) = with(scope) {
        if (!hasSize || rows == 0) return
        val intensity = params.intensity.coerceIn(0.1f, 2f)
        val baseLen = min(cellW, cellH) * 0.5f
        var i = 0
        for (r in 0 until rows) {
            val cy = (r + 0.5f) * cellH
            for (c in 0 until cols) {
                val cx = (c + 0.5f) * cellW
                val e = columnEnergy[c]
                val half = baseLen * (0.35f + 0.75f * e * intensity + 0.4f * flash)
                val a = angle[i]
                val dx = cos(a) * half
                val dy = sin(a) * half
                drawLine(
                    color = columnColors[c],
                    start = Offset(cx - dx, cy - dy),
                    end = Offset(cx + dx, cy + dy),
                    strokeWidth = unit * (0.9f + 1.6f * e),
                    cap = StrokeCap.Round,
                    alpha = (0.22f + 0.7f * e + 0.15f * flash).coerceAtMost(1f),
                )
                i++
            }
        }
    }

    companion object {
        const val ID = "lines"
        private const val HALF_PI = (PI / 2).toFloat()
        private const val TWO_PI = (PI * 2).toFloat()
    }
}
