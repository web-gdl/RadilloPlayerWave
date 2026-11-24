package com.radillo.playerwave.visualizer.scenes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import com.radillo.playerwave.visualizer.analysis.AudioFrame
import com.radillo.playerwave.visualizer.engine.Argb
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Escena 1: **barras LED** como el «SPECTRUM DISPLAY» de un ecualizador Hi-Fi clásico. Son
 * [COLUMNS] bandas por octava (31 Hz – 16 kHz) y [ROWS] LED redondos por banda: 3 verdes, 3 ámbar
 * y 3 rojos. Hay escala 2–18 dB a ambos lados y frecuencias abajo. El último LED de pico se queda
 * encendido un momento y cae con peso. Usa siempre los colores clásicos: la paleta no la tiñe.
 *
 * Movimiento (0.20.2): entrada suavizada (~60 ms), subida casi inmediata y bajada a velocidad
 * constante, como un medidor; y el LED de arriba se enciende con brillo parcial según la fracción
 * del nivel, así la columna sube y baja fluida aunque solo tenga 9 LED.
 *
 * Pensada para dibujar con la CPU del autoestéreo:
 * - el panel (marco, título, cristal, escalas y los LED apagados) se pinta **una vez por tamaño**
 *   en un mapa de bits; los cambios de calidad del gobernador no lo tocan. La 0.20.0 rehacía
 *   todo en cada ajuste y daba tirones;
 * - cada LED encendido es un sprite pequeño ya pintado (núcleo + resplandor), copiado en
 *   coordenadas enteras sin filtrado: el camino más rápido. Como mucho son 90 copias por frame.
 */
class SpectrumBarsScene : CanvasScene(id = ID, displayName = "Barras LED") {

    private val levels = FloatArray(COLUMNS)
    private val peaks = FloatArray(COLUMNS)
    private val peakHold = FloatArray(COLUMNS)
    private val peakVelocity = FloatArray(COLUMNS)
    private val smoothed = FloatArray(COLUMNS)
    private val spritePaint = Paint()

    /** Rango de bandas del analizador (posiciones fraccionarias) que cubre cada octava. */
    private val bandFrom = FloatArray(COLUMNS)
    private val bandTo = FloatArray(COLUMNS)
    private var mappedBandCount = -1

    private var panel: Bitmap? = null
    private val sprites = arrayOfNulls<Bitmap>(3)
    private var panelKey = ""
    private val dotX = IntArray(COLUMNS)
    private val dotY = IntArray(ROWS)
    private var spriteHalf = 0

    /** Veces que se ha pintado el panel (pruebas: no debe repetirse al cambiar la calidad). */
    internal var panelBuilds = 0
        private set

    /** Nivel actual (0..1) de la columna [column] (pruebas). */
    internal fun level(column: Int): Float = levels[column]

    override val coversBackground: Boolean get() = panel != null

    override fun rebuild() {
        if (!hasSize) return
        // Escalas y título solo en horizontal: en vertical (móvil) se encimaban (0.23.0).
        val labels = width >= 480 && height >= 260 && width >= height
        val key = "${width}x$height/$labels"
        if (key == panelKey) return
        panelKey = key
        panelBuilds++
        paintPanel(labels)
    }

    private fun paintPanel(labels: Boolean) {
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = w * if (labels) 0.035f else 0.04f
        val top = if (labels) h * 0.16f else pad
        val bottom = h - pad
        val left = pad
        val right = w - pad
        val sideLabel = (right - left) * if (labels) 0.11f else 0.04f
        val bottomLabel = (bottom - top) * if (labels) 0.16f else 0.06f
        val gx0 = left + sideLabel
        val gx1 = right - sideLabel
        val gy0 = top + (bottom - top) * 0.08f
        val gy1 = bottom - bottomLabel
        val cellW = (gx1 - gx0) / COLUMNS
        val cellH = (gy1 - gy0) / ROWS
        val radius = max(2f, min(cellW, cellH) * 0.12f)
        for (i in 0 until COLUMNS) dotX[i] = (gx0 + (i + 0.5f) * cellW).roundToInt()
        for (r in 0 until ROWS) dotY[r] = (gy1 - (r + 0.5f) * cellH).roundToInt()

        // Sprites de LED encendido: resplandor + núcleo + brillo, uno por color.
        val glow = radius * 4.2f
        spriteHalf = ceil(glow).toInt()
        val size = spriteHalf * 2 + 1
        for (k in 0 until 3) sprites[k] = paintSprite(size, radius, glow, LED_COLORS[k])

        // En las pruebas de JVM (stubs del SDK) createBitmap devuelve null: no hay nada que pintar.
        val created: Bitmap? = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val bitmap = created ?: run {
            panel = null
            return
        }
        val c = Canvas(bitmap)
        // Marco de metal cepillado.
        val frame = fillPaint().apply {
            fillShader = LinearGradient(0f, 0f, 0f, h, FRAME_TOP, FRAME_BOTTOM, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w, h, frame)
        val brush = Paint()
        val random = Random(3)
        var y = 0f
        while (y < h) {
            brush.color = Argb.withAlpha(0xFFFFFFFF.toInt(), 0.012f + 0.012f * random.nextFloat())
            c.drawRect(0f, y, w, y + 1f, brush)
            y += 2f
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val face: Typeface? = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            typeface = face
            color = LABEL
        }
        if (labels) {
            // Título con su corchete, como en el panel del ecualizador.
            text.textSize = h * 0.042f
            text.textAlign = Paint.Align.CENTER
            val titleY = h * 0.075f
            c.drawText(TITLE, w * 0.5f, titleY + h * 0.015f, text)
            val half = text.measureText(TITLE) * 0.5f + h * 0.026f
            val bracket = strokePaint(max(1.5f, h * 0.004f), rounded = false).apply { color = BRACKET }
            val bl = left + w * 0.04f
            val br = right - w * 0.04f
            val tick = h * 0.03f
            c.drawLine(bl, titleY + tick, bl, titleY, bracket)
            c.drawLine(bl, titleY, w * 0.5f - half, titleY, bracket)
            c.drawLine(w * 0.5f + half, titleY, br, titleY, bracket)
            c.drawLine(br, titleY, br, titleY + tick, bracket)
        }
        // Ventana de cristal negro con un reflejo arriba.
        c.drawRect(left, top, right, bottom, fillPaint().apply { color = GLASS })
        val reflection = fillPaint().apply {
            fillShader = LinearGradient(
                0f, top, 0f, top + (bottom - top) * 0.3f,
                Argb.withAlpha(0xFFFFFFFF.toInt(), 0.05f), Argb.TRANSPARENT, Shader.TileMode.CLAMP,
            )
        }
        c.drawRect(left, top, right, bottom, reflection)
        c.drawRect(left, top, right, bottom, strokePaint(max(1.5f, h * 0.0056f), rounded = false).apply { color = GLASS_EDGE })

        if (labels) {
            text.textSize = h * 0.04f
            for (r in 0 until ROWS) {
                val label = if (r == 0) "2dB" else (2 + 2 * r).toString()
                val ty = dotY[r] + text.textSize * 0.36f
                text.textAlign = Paint.Align.RIGHT
                c.drawText(label, gx0 - cellW * 0.15f, ty, text)
                text.textAlign = Paint.Align.LEFT
                c.drawText(label, gx1 + cellW * 0.12f, ty, text)
            }
            text.textAlign = Paint.Align.CENTER
            for (i in 0 until COLUMNS) c.drawText(HZ_LABELS[i], dotX[i].toFloat(), gy1 + bottomLabel * 0.62f, text)
        }
        // LED apagados: se ven oscuros detrás del cristal.
        val off = fillPaint().apply { color = LED_OFF }
        val offShine = fillPaint().apply { color = Argb.withAlpha(0xFFFFFFFF.toInt(), 0.05f) }
        for (i in 0 until COLUMNS) {
            for (r in 0 until ROWS) {
                val x = dotX[i].toFloat()
                val yy = dotY[r].toFloat()
                c.drawCircle(x, yy, radius, off)
                c.drawCircle(x - radius * 0.2f, yy - radius * 0.2f, radius * 0.5f, offShine)
            }
        }
        panel = bitmap
    }

    private fun paintSprite(size: Int, radius: Float, glow: Float, color: Int): Bitmap? {
        val created: Bitmap? = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val bitmap = created ?: return null
        val c = Canvas(bitmap)
        val center = size * 0.5f
        val halo = fillPaint().apply {
            fillShader = RadialGradient(
                center, center, glow,
                intArrayOf(Argb.withAlpha(color, 0.8f), Argb.withAlpha(color, 0.33f), Argb.withAlpha(color, 0f)),
                floatArrayOf(0f, 0.25f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        c.drawCircle(center, center, glow, halo)
        c.drawCircle(center, center, radius, fillPaint().apply { this.color = color })
        c.drawCircle(
            center - radius * 0.25f, center - radius * 0.25f, radius * 0.38f,
            fillPaint().apply { this.color = Argb.withAlpha(0xFFFFFFFF.toInt(), 0.75f) },
        )
        return bitmap
    }

    /** Calcula qué bandas del analizador caen en cada octava (de f/√2 a f·√2). */
    private fun mapBands(n: Int) {
        mappedBandCount = n
        val span = ln(MAX_HZ / MIN_HZ)
        val last = (n - 1).toFloat()
        for (i in 0 until COLUMNS) {
            val f = CENTER_HZ[i]
            bandFrom[i] = (ln(f / SQRT2 / MIN_HZ) / span * last).coerceIn(0f, last)
            bandTo[i] = (ln(f * SQRT2 / MIN_HZ) / span * last).coerceIn(0f, last)
        }
    }

    override fun update(frame: AudioFrame, deltaSeconds: Float) {
        val bands = frame.bands
        val n = bands.size
        if (n == 0) return
        if (n != mappedBandCount) mapBands(n)
        val sensitivity = params.sensitivity
        val speed = params.speed
        val inputK = smoothing(deltaSeconds, INPUT_RATE * speed)
        val attackK = smoothing(deltaSeconds, ATTACK_RATE * speed)
        val release = RELEASE_PER_SECOND * speed * deltaSeconds
        for (i in 0 until COLUMNS) {
            // Energía de la octava: sobre todo su banda más fuerte, algo del promedio.
            val from = bandFrom[i].toInt()
            val to = ceil(bandTo[i]).toInt().coerceIn(from, n - 1)
            var peak = 0f
            var sum = 0f
            for (b in from..to) {
                val v = bands[b]
                if (v > peak) peak = v
                sum += v
            }
            val raw = (0.65f * peak + 0.35f * sum / (to - from + 1)) * sensitivity
            // Los 9 LED cubren la parte útil de la escala (unos 48 dB), no los 60 dB completos:
            // así la música se mueve por toda la columna en vez de bailar solo arriba.
            val target = ((raw - FLOOR) / (1f - FLOOR)).coerceIn(0f, 1f)
            // 1) Suavizar la entrada: quita el temblor de frame a frame del análisis.
            smoothed[i] += (target - smoothed[i]) * inputK
            // 2) Balística de medidor: sube rápido con el golpe y baja a velocidad constante, como
            //    un ecualizador de verdad (la bajada exponencial de antes «saltaba» de LED en LED).
            val current = levels[i]
            val goal = smoothed[i]
            levels[i] = if (goal > current) current + (goal - current) * attackK else max(goal, current - release)
            // 3) Pico: se queda un momento y cae acelerando, como si tuviera peso.
            val v = levels[i]
            if (v >= peaks[i]) {
                peaks[i] = v
                peakHold[i] = PEAK_HOLD_SECONDS
                peakVelocity[i] = 0f
            } else if (peakHold[i] > 0f) {
                peakHold[i] -= deltaSeconds
            } else {
                peakVelocity[i] += PEAK_GRAVITY * speed * deltaSeconds
                peaks[i] = max(v, peaks[i] - peakVelocity[i] * deltaSeconds)
            }
        }
    }

    override fun draw(canvas: Canvas) {
        val back = panel ?: return
        canvas.drawBitmap(back, 0f, 0f, null)
        val intensity = params.intensity.coerceIn(0.1f, 2f)
        for (i in 0 until COLUMNS) {
            val x = (dotX[i] - spriteHalf).toFloat()
            // Nivel continuo en LED: los enteros se encienden del todo y el siguiente LED se va
            // encendiendo poco a poco con la fracción, para que la columna suba y baje suave.
            val exact = (levels[i] * intensity).coerceIn(0f, 1f) * ROWS
            val full = exact.toInt().coerceAtMost(ROWS)
            val fraction = exact - full
            for (r in 0 until full) blit(canvas, r, x, 255)
            if (full < ROWS && fraction > MIN_FRACTION) blit(canvas, full, x, alpha255(fraction))
            val peakRow = ceil((peaks[i] * intensity).coerceIn(0f, 1f) * ROWS).toInt() - 1
            if (peakRow > full) blit(canvas, peakRow.coerceAtMost(ROWS - 1), x, 255)
        }
    }

    private fun blit(canvas: Canvas, row: Int, x: Float, alpha: Int) {
        val sprite = sprites[colorIndex(row)] ?: return
        spritePaint.alpha = alpha
        canvas.drawBitmap(sprite, x, (dotY[row] - spriteHalf).toFloat(), spritePaint)
    }

    override fun onDetach() {
        // Sin recycle(): un lienzo con aceleración podría seguir referenciándolos; basta soltarlos.
        panel = null
        sprites.fill(null)
        panelKey = ""
    }

    companion object {
        const val ID = "spectrum"

        /** Bandas por octava, de 31 Hz a 16 kHz. */
        const val COLUMNS = 10

        /** LED por banda: 3 verdes, 3 ámbar y 3 rojos (escala 2–18 dB). */
        const val ROWS = 9

        private const val TITLE = "SPECTRUM DISPLAY"
        private const val PEAK_HOLD_SECONDS = 0.45f
        /** Aceleración de caída del pico (escala completa por segundo²). */
        private const val PEAK_GRAVITY = 3.2f
        /** Suavizado de la entrada (1/s): ~60 ms, quita el temblor sin retrasar el golpe. */
        private const val INPUT_RATE = 16f
        /** Subida (1/s): casi inmediata con el golpe. */
        const val ATTACK_RATE = 40f
        /** Bajada constante: la columna entera tarda ~0,9 s en vaciarse. */
        const val RELEASE_PER_SECOND = 1.1f
        /** Parte baja de la escala (−60..−48 dB) que no enciende ningún LED. */
        private const val FLOOR = 0.2f
        /** Por debajo de esta fracción el LED parcial no se dibuja (evita un parpadeo tenue). */
        private const val MIN_FRACTION = 0.08f
        private const val MIN_HZ = 30f
        private const val MAX_HZ = 16_000f
        private val SQRT2 = sqrt(2f)
        private val CENTER_HZ = floatArrayOf(31.5f, 63f, 125f, 250f, 500f, 1_000f, 2_000f, 4_000f, 8_000f, 16_000f)
        private val HZ_LABELS = arrayOf("31Hz", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16KHz")

        private const val GREEN = 0xFF3DFF63.toInt()
        private const val AMBER = 0xFFFFB322.toInt()
        private const val RED = 0xFFFF2D62.toInt()
        private val LED_COLORS = intArrayOf(GREEN, AMBER, RED)
        private const val LED_OFF = 0xFF1A1B1E.toInt()
        private const val FRAME_TOP = 0xFF1D1F23.toInt()
        private const val FRAME_BOTTOM = 0xFF0E0F12.toInt()
        private const val GLASS = 0xFF050506.toInt()
        private const val GLASS_EDGE = 0xFF3A3D44.toInt()
        private const val LABEL = 0xFFE6E8EC.toInt()
        private const val BRACKET = 0xFFC9CCD2.toInt()

        /** 0 = verde (LED 1–3), 1 = ámbar (4–6), 2 = rojo (7–9). */
        fun colorIndex(row: Int): Int = when {
            row >= 6 -> 2
            row >= 3 -> 1
            else -> 0
        }

        fun ledColor(row: Int): Int = LED_COLORS[colorIndex(row)]

        /** LED encendidos para un nivel 0..1. */
        fun segments(level: Float): Int = (level * ROWS + 0.35f).toInt().coerceIn(0, ROWS)
    }
}
