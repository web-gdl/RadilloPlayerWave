package com.radillo.playerwave.visualizer.scenes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.Shader
import com.radillo.playerwave.visualizer.analysis.AudioFrame
import com.radillo.playerwave.visualizer.engine.Argb
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Escena 4: **terreno synthwave**. Una malla en perspectiva avanza hacia quien mira; a los lados de
 * un camino central se levantan montañas con el espectro del momento (graves junto al camino,
 * agudos hacia los bordes), que nacen en el horizonte y se acercan. Al fondo, cielo degradado con
 * estrellas y un sol retro con franjas. Los colores salen de la paleta.
 *
 * Es «3D» dibujado con líneas en el lienzo, sin OpenGL: pensado para la CPU del autoestéreo.
 * Cielo, estrellas y sol se pintan una sola vez en un mapa de bits ([rebuild]); cada frame solo
 * proyecta la malla. Las franjas entre filas se rellenan de lejos a cerca con el color del suelo
 * para que las montañas cercanas tapen las líneas de detrás. Con calidad baja hay menos filas y
 * columnas, y se apagan el resplandor de las filas cercanas y el brillo del horizonte. En gama baja
 * (8227L) la malla es fija de 14 × 24, con líneas finas y suelo sin antialias. Toda la malla late
 * con los graves del momento, para que el ritmo se note al instante.
 */
class TerrainScene : CanvasScene(id = ID, displayName = "Terreno synthwave") {

    private var rows = 24
    private var cols = 40
    private var history = Array(rows) { FloatArray(cols + 1) }
    private var live = FloatArray(cols + 1)
    private var target = FloatArray(cols + 1)
    private var xs = FloatArray(rows * (cols + 1))
    private var ys = FloatArray(rows * (cols + 1))
    private var fades = FloatArray(rows)
    private var segments = FloatArray((cols + 1) * 4)
    private var head = 0
    private var phase = 0f

    private var background: Bitmap? = null
    private var backgroundKey = ""

    /** Veces que se ha pintado el fondo (pruebas: no debe repetirse al cambiar la calidad). */
    internal var backgroundBuilds = 0
        private set
    private var horizonY = 0f
    private var groundHeight = 0f
    private var lineBase = 1f
    private var bass = 0f
    private var punch = 0f

    private val strip = Path()
    private val rowLine = Path()
    private val groundPaint = fillPaint()
    private val rowPaint = strokePaint(2f)
    private val rowGlowPaint = strokePaint(6f)
    private val columnPaint = strokePaint(1f, rounded = false)
    private val horizonPaint = fillPaint()
    private var rowColorA = 0
    private var rowColorB = 0
    private var drawGlow = true
    private var drawHorizonGlow = true

    override fun rebuild() {
        // Gama baja: malla fija de 14 × 24; si no, según densidad en pasos que no cambian con cada
        // pequeño ajuste del gobernador.
        val newRows = if (lowEnd) LOW_END_ROWS else quantize((30f * params.effectiveDensity + 8f).roundToInt(), 2).coerceIn(10, 30)
        val newCols = if (lowEnd) LOW_END_COLS else quantize((56f * params.effectiveDensity + 10f).roundToInt(), 4).coerceIn(16, 52)
        if (newRows != rows || newCols != cols || xs.size != newRows * (newCols + 1)) {
            rows = newRows
            cols = newCols
            history = Array(rows) { FloatArray(cols + 1) }
            live = FloatArray(cols + 1)
            target = FloatArray(cols + 1)
            xs = FloatArray(rows * (cols + 1))
            ys = FloatArray(rows * (cols + 1))
            fades = FloatArray(rows)
            segments = FloatArray((cols + 1) * 4)
            head = 0
        }
        drawGlow = !lowEnd && params.quality >= GLOW_MIN_QUALITY
        drawHorizonGlow = params.quality >= HORIZON_MIN_QUALITY
        // El suelo entre filas no necesita bordes suavizados (los tapan las líneas): rellenar sin
        // antialias es bastante más barato dibujando con la CPU.
        groundPaint.isAntiAlias = !lowEnd
        val pal = palette.colors
        rowColorA = pal.first()
        rowColorB = pal.last()
        columnPaint.color = rowColorB
        val ground = Argb.lerp(palette.background, pal[pal.size / 2], 0.07f)
        groundPaint.color = ground
        if (!hasSize) return
        val w = width.toFloat()
        val h = height.toFloat()
        horizonY = h * HORIZON
        groundHeight = h - horizonY
        lineBase = max(1f, min(w, h) * 0.0028f)
        rowGlowPaint.strokeWidth = lineBase * 5f
        horizonPaint.fillShader = LinearGradient(
            0f, horizonY - h * 0.08f, 0f, horizonY + h * 0.05f,
            intArrayOf(Argb.TRANSPARENT, Argb.withAlpha(rowColorB, 0.6f), Argb.TRANSPARENT),
            floatArrayOf(0f, 0.62f, 1f),
            Shader.TileMode.CLAMP,
        )
        // El fondo (cielo, estrellas, sol) solo depende del tamaño y la paleta: no se vuelve a pintar
        // cuando el gobernador cambia la calidad (antes eso daba un tirón en cada ajuste).
        val key = "${width}x$height/${palette.id}"
        if (key != backgroundKey) {
            background = paintBackground(w, h, ground)
            backgroundKey = key
            backgroundBuilds++
        }
    }

    override val coversBackground: Boolean get() = background != null

    /** Cielo, estrellas, sol con franjas y suelo: se pinta una vez por tamaño o paleta. */
    private fun paintBackground(w: Float, h: Float, ground: Int): Bitmap? {
        // En las pruebas de JVM (stubs del SDK) createBitmap devuelve null.
        val created: Bitmap? = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val bitmap = created ?: return null
        val c = Canvas(bitmap)
        val pal = palette.colors
        val skyTop = palette.background
        val skyMid = Argb.lerp(palette.background, pal[pal.size - 2], 0.3f)
        val skyLow = Argb.lerp(pal.last(), palette.background, 0.3f)
        val sky = fillPaint().apply {
            fillShader = LinearGradient(0f, 0f, 0f, horizonY, intArrayOf(skyTop, skyMid, skyLow), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w, horizonY, sky)
        val star = fillPaint()
        val size = max(1f, min(w, h) * 0.003f)
        val random = Random(4)
        repeat((w * h / 7000f).roundToInt().coerceIn(12, 90)) {
            star.color = Argb.withAlpha(palette.accent, 0.25f + 0.6f * random.nextFloat())
            val x = random.nextFloat() * w
            val y = random.nextFloat() * horizonY * 0.78f
            c.drawRect(x, y, x + size, y + size, star)
        }

        // Sol retro: degradado de arriba abajo y franjas horizontales cada vez más gruesas.
        val radius = min(w * 0.12f, horizonY * 0.75f)
        val sunX = w * 0.5f
        val sunY = horizonY - radius * 0.22f
        c.save()
        val circle = Path().apply { addCircle(sunX, sunY, radius, Path.Direction.CW) }
        c.clipPath(circle)
        val sun = fillPaint().apply {
            fillShader = LinearGradient(
                0f, sunY - radius, 0f, sunY + radius,
                intArrayOf(Argb.lerp(palette.accent, 0xFFFFE96A.toInt(), 0.5f), pal[pal.size - 2], pal.last()),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        c.drawRect(sunX - radius, sunY - radius, sunX + radius, sunY + radius, sun)
        val cut = fillPaint().apply { color = skyMid }
        for (k in 0 until 7) {
            val y = sunY + radius * (0.08f + k * 0.15f)
            c.drawRect(sunX - radius, y, sunX + radius, y + radius * (0.02f + k * 0.015f), cut)
        }
        c.restore()

        c.drawRect(0f, horizonY, w, h, fillPaint().apply { color = ground })
        return bitmap
    }

    override fun update(frame: AudioFrame, deltaSeconds: Float) {
        val bands = frame.bands
        val n = bands.size
        val sensitivity = params.sensitivity
        for (c in 0..cols) {
            val u = c.toFloat() / cols * 2f - 1f
            val a = if (u < 0f) -u else u
            target[c] = if (a < ROAD) {
                0f
            } else {
                val t = (a - ROAD) / (1f - ROAD)
                val ramp = min(1f, t / 0.22f).let { it * it * (3f - 2f * it) }
                (sampleLinear(bands, t * (n - 1)) * sensitivity).coerceIn(0f, 1f) * ramp
            }
        }
        val rise = smoothing(deltaSeconds, 18f * params.speed)
        val fall = smoothing(deltaSeconds, 6f * params.speed)
        for (c in 0..cols) live[c] = follow(live[c], target[c], rise, fall)

        phase += deltaSeconds * SCROLL_ROWS_PER_SECOND * params.speed
        var pushes = 0
        while (phase >= 1f) {
            phase -= 1f
            if (pushes++ < rows) {
                head = (head + 1) % rows
                live.copyInto(history[head])
            }
        }
        val bassNow = (frame.bass * sensitivity).coerceIn(0f, 1f)
        bass += (bassNow - bass) * smoothing(deltaSeconds, 10f)
        // «Golpe»: sube al instante con los graves y baja despacio. Hace latir toda la malla con el
        // ritmo **ahora**, aunque las montañas tarden unos segundos en llegar del horizonte.
        punch = follow(punch, bassNow, smoothing(deltaSeconds, 40f), smoothing(deltaSeconds, 5f * params.speed))
    }

    override fun draw(canvas: Canvas) {
        if (!hasSize || groundHeight <= 0f) return
        val back = background
        if (back != null) canvas.drawBitmap(back, 0f, 0f, null)
        if (drawHorizonGlow) {
            horizonPaint.alpha = alpha255(0.45f + 0.55f * bass)
            canvas.drawRect(0f, horizonY - height * 0.08f, width.toFloat(), horizonY + height * 0.05f, horizonPaint)
        }
        project()

        val stride = cols + 1
        for (j in 0 until rows) {
            val fade = fades[j]
            if (j > 0) {
                // Suelo entre la fila anterior (más lejos) y esta: tapa lo que queda detrás.
                strip.rewind()
                val prev = (j - 1) * stride
                val cur = j * stride
                strip.moveTo(xs[prev], ys[prev])
                for (c in 1..cols) strip.lineTo(xs[prev + c], ys[prev + c])
                for (c in cols downTo 0) strip.lineTo(xs[cur + c], ys[cur + c])
                strip.close()
                canvas.drawPath(strip, groundPaint)

                var s = 0
                for (c in 0..cols) {
                    segments[s++] = xs[prev + c]
                    segments[s++] = ys[prev + c]
                    segments[s++] = xs[cur + c]
                    segments[s++] = ys[cur + c]
                }
                // Gama baja: líneas de 1 px (el trazo más rápido con la CPU).
                columnPaint.strokeWidth = if (lowEnd) 0f else lineBase * (0.6f + 0.9f * fade)
                columnPaint.alpha = alpha255(0.12f + 0.5f * fade)
                canvas.drawLines(segments, 0, s, columnPaint)
            }
            val base = j * stride
            val color = if ((head - j) and 1 == 0) rowColorA else rowColorB
            if (lowEnd) {
                // Segmentos sueltos en vez de una ruta con uniones redondeadas: mucho más barato.
                var s = 0
                for (c in 0 until cols) {
                    segments[s++] = xs[base + c]
                    segments[s++] = ys[base + c]
                    segments[s++] = xs[base + c + 1]
                    segments[s++] = ys[base + c + 1]
                }
                rowPaint.color = color
                rowPaint.alpha = alpha255(0.25f + 0.75f * fade)
                rowPaint.strokeWidth = if (fade > 0.55f) lineBase * 1.6f else 0f
                canvas.drawLines(segments, 0, s, rowPaint)
                continue
            }
            rowLine.rewind()
            rowLine.moveTo(xs[base], ys[base])
            for (c in 1..cols) rowLine.lineTo(xs[base + c], ys[base + c])
            if (drawGlow && fade > 0.45f) {
                rowGlowPaint.color = Argb.withAlpha(color, 0.22f * fade)
                canvas.drawPath(rowLine, rowGlowPaint)
            }
            rowPaint.color = color
            rowPaint.alpha = alpha255(0.2f + 0.8f * fade)
            rowPaint.strokeWidth = lineBase * (0.7f + 1.3f * fade)
            canvas.drawPath(rowLine, rowPaint)
        }
    }

    /** Proyecta la malla: fila 0 = horizonte (lo más nuevo), última fila = junto a quien mira. */
    private fun project() {
        val w = width.toFloat()
        val centerX = w * 0.5f
        val zFar = 1f / (1f + rows * DEPTH)
        val zRef = 1f / (1f + DEPTH)
        val hill = groundHeight * HILL * params.intensity.coerceIn(0.1f, 2f) * (0.7f + 0.6f * punch)
        val stride = cols + 1
        for (j in 0 until rows) {
            val distance = (rows - 1 - j) + (1f - phase)
            val z = 1f / (1f + distance * DEPTH)
            val t = ((z - zFar) / (zRef - zFar)).coerceAtLeast(0f)
            val scale = z / zRef
            fades[j] = t.coerceAtMost(1f)
            val baseY = horizonY + groundHeight * t
            val heights = if (j == 0) live else history[(head - j + 1 + rows) % rows]
            val row = j * stride
            for (c in 0..cols) {
                val u = c.toFloat() / cols * 2f - 1f
                xs[row + c] = centerX + u * w * WIDTH * scale
                ys[row + c] = baseY - heights[c] * hill * scale
            }
        }
    }

    override fun onDetach() {
        background = null
        backgroundKey = ""
    }

    companion object {
        const val ID = "terrain"
        const val GLOW_MIN_QUALITY = 0.7f
        const val HORIZON_MIN_QUALITY = 0.55f

        /** Altura del horizonte (fracción del alto). */
        private const val HORIZON = 0.46f
        /** Mitad del ancho del camino central sin montañas (fracción de la malla). */
        private const val ROAD = 0.16f
        private const val DEPTH = 0.24f
        private const val WIDTH = 0.62f
        private const val HILL = 0.55f
        private const val SCROLL_ROWS_PER_SECOND = 2.4f
        const val LOW_END_ROWS = 14
        const val LOW_END_COLS = 24
    }
}
