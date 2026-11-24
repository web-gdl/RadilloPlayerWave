package com.radillo.playerwave.visualizer.scenes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import com.radillo.playerwave.visualizer.analysis.AudioFrame
import com.radillo.playerwave.visualizer.engine.Argb
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Escena 2: **barras modernas**. Cada barra es una pila de líneas horizontales delgadas, con el
 * color de la paleta de graves a agudos, más brillante arriba. Detrás se ve la pista apagada. Una
 * línea blanca de pico flota sobre cada barra y cae con peso.
 *
 * Mismo movimiento que las Barras LED: entrada suavizada, subida rápida y bajada constante. La
 * barra no salta de línea en línea: se recorta al píxel, así sube y baja continua.
 *
 * Ligera para la CPU del autoestéreo, igual que las Barras LED: el fondo (con las pistas
 * apagadas) y una copia de todas las barras encendidas se pintan **una vez** por tamaño, número
 * de barras y paleta. Cada frame solo copia, para cada barra, el trozo encendido (sin escalar) y
 * dibuja su línea de pico. En gama baja hay 20 barras anchas fijas, así que los ajustes de calidad del
 * gobernador no reconstruyen nada.
 */
class ModernBarsScene : CanvasScene(id = ID, displayName = "Barras modernas") {

    private var count = LOW_END_BARS
    private var levels = FloatArray(count)
    private var smoothed = FloatArray(count)
    private var peaks = FloatArray(count)
    private var peakHold = FloatArray(count)
    private var peakVelocity = FloatArray(count)
    private var barLeft = IntArray(count)

    private var background: Bitmap? = null
    private var lit: Bitmap? = null
    private var cacheKey = ""
    private var barWidth = 0
    private var floorY = 0
    private var maxHeight = 0
    private var lineHeight = 2
    private val src = Rect()
    private val dst = Rect()
    private val peakPaint = Paint().apply { color = 0xEBFFFFFF.toInt() }

    /** Veces que se han pintado fondo y barras (pruebas: no debe repetirse al cambiar la calidad). */
    internal var cacheBuilds = 0
        private set

    /** Nivel actual (0..1) de la barra [bar] (pruebas). */
    internal fun level(bar: Int): Float = levels[bar]

    internal val barCount: Int get() = count

    override val coversBackground: Boolean get() = background != null

    override fun rebuild() {
        val base = if (lowEnd) LOW_END_BARS else quantize((32f * params.effectiveDensity).roundToInt(), 4).coerceIn(16, 40)
        // Mismas proporciones en cualquier tamaño: el número de barras sigue la forma de la
        // pantalla (20 en la pantalla completa del estéreo, unas 10 en la portada cuadrada).
        val newCount = if (hasSize) barsFor(base, width, height) else base
        if (newCount != count) {
            count = newCount
            levels = FloatArray(count)
            smoothed = FloatArray(count)
            peaks = FloatArray(count)
            peakHold = FloatArray(count)
            peakVelocity = FloatArray(count)
            barLeft = IntArray(count)
        }
        if (!hasSize) return
        val key = "${width}x$height/$count/${palette.id}"
        if (key == cacheKey) return
        cacheKey = key
        cacheBuilds++
        paintCaches()
    }

    private fun paintCaches() {
        val w = width.toFloat()
        val h = height.toFloat()
        val margin = w * 0.05f
        // Barras anchas con aire entre ellas: cada barra ocupa el 68 % de su hueco.
        val slot = (w - 2f * margin) / count
        val bw = slot * BAR_FILL
        val gap = slot - bw
        barWidth = max(1, bw.toInt())
        floorY = (h * 0.88f).roundToInt()
        maxHeight = (floorY - h * 0.10f).roundToInt().coerceAtLeast(1)
        lineHeight = max(2, (h * 0.006f).roundToInt())
        val pitch = lineHeight + max(1, (lineHeight * 0.75f).roundToInt())
        for (i in 0 until count) barLeft[i] = (margin + gap * 0.5f + i * slot).roundToInt()

        // En las pruebas de JVM (stubs del SDK) createBitmap devuelve null: no hay nada que pintar.
        val backCreated: Bitmap? = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val litCreated: Bitmap? = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (backCreated == null || litCreated == null) {
            background = null
            lit = null
            return
        }
        val back = Canvas(backCreated)
        back.drawColor(palette.background)
        val pal = palette.colors
        val vignette = fillPaint().apply {
            fillShader = RadialGradient(
                w * 0.5f, h * 0.55f, w * 0.7f,
                Argb.withAlpha(Argb.lerp(palette.background, pal[pal.size / 2], 0.18f), 1f), palette.background,
                Shader.TileMode.CLAMP,
            )
        }
        back.drawRect(0f, 0f, w, h, vignette)
        val track = Paint().apply { color = Argb.withAlpha(0xFFFFFFFF.toInt(), 0.045f) }
        val line = Paint()
        val litCanvas = Canvas(litCreated)
        for (i in 0 until count) {
            val x0 = barLeft[i].toFloat()
            val x1 = x0 + barWidth
            val t = if (count > 1) i.toFloat() / (count - 1) * (pal.size - 1) else 0f
            val k = t.toInt().coerceAtMost(pal.size - 2)
            val color = Argb.lerp(pal[k], pal[k + 1], t - k)
            var y = floorY
            while (y > floorY - maxHeight) {
                val top = (y - lineHeight).toFloat()
                back.drawRect(x0, top, x1, y.toFloat(), track)
                // Más brillante y opaca arriba, más tenue abajo.
                val up = (floorY - y).toFloat() / maxHeight
                line.color = Argb.withAlpha(Argb.lerp(color, 0xFFFFFFFF.toInt(), 0.35f * up), 0.55f + 0.45f * up)
                litCanvas.drawRect(x0, top, x1, y.toFloat(), line)
                y -= pitch
            }
        }
        val base = Paint().apply { color = Argb.withAlpha(0xFFFFFFFF.toInt(), 0.18f) }
        back.drawRect(margin, floorY + 3f, w - margin, floorY + 4f, base)
        background = backCreated
        lit = litCreated
    }

    override fun update(frame: AudioFrame, deltaSeconds: Float) {
        val bands = frame.bands
        val n = bands.size
        if (n == 0) return
        val sensitivity = params.sensitivity
        val speed = params.speed
        val inputK = smoothing(deltaSeconds, INPUT_RATE * speed)
        val attackK = smoothing(deltaSeconds, ATTACK_RATE * speed)
        val release = RELEASE_PER_SECOND * speed * deltaSeconds
        for (i in 0 until count) {
            val pos = if (count > 1) i.toFloat() / (count - 1) * (n - 1) else 0f
            val raw = sampleLinear(bands, pos) * sensitivity
            val target = ((raw - FLOOR) / (1f - FLOOR)).coerceIn(0f, 1f)
            smoothed[i] += (target - smoothed[i]) * inputK
            val current = levels[i]
            val goal = smoothed[i]
            levels[i] = if (goal > current) current + (goal - current) * attackK else max(goal, current - release)
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
        val back = background ?: return
        val bars = lit ?: return
        canvas.drawBitmap(back, 0f, 0f, null)
        val intensity = params.intensity.coerceIn(0.1f, 2f)
        for (i in 0 until count) {
            val x = barLeft[i]
            val height = ((levels[i] * intensity).coerceIn(0f, 1f) * maxHeight).toInt()
            if (height > 0) {
                src.set(x, floorY - height, x + barWidth, floorY)
                dst.set(src)
                canvas.drawBitmap(bars, src, dst, null)
            }
            val peak = ceil((peaks[i] * intensity).coerceIn(0f, 1f) * maxHeight).toInt()
            if (peak > height + lineHeight) {
                val top = (floorY - peak - lineHeight).toFloat()
                canvas.drawRect(x.toFloat(), top, (x + barWidth).toFloat(), top + lineHeight, peakPaint)
            }
        }
    }

    override fun onDetach() {
        background = null
        lit = null
        cacheKey = ""
    }

    companion object {
        const val ID = "modern_bars"
        const val LOW_END_BARS = 20

        /** Forma de referencia: la pantalla útil del estéreo (1024 × 534). */
        private const val REFERENCE_ASPECT = 1024f / 534f

        /** Barras para un lienzo de [w]×[h] partiendo de [base] barras en la forma de referencia. */
        fun barsFor(base: Int, w: Int, h: Int): Int {
            val factor = ((w.toFloat() / h) / REFERENCE_ASPECT).coerceIn(0.35f, 1.6f)
            return (base * factor).roundToInt().coerceAtLeast(6)
        }
        private const val BAR_FILL = 0.68f
        private const val PEAK_HOLD_SECONDS = 0.45f
        private const val PEAK_GRAVITY = 3.2f
        private const val INPUT_RATE = 16f
        private const val ATTACK_RATE = 40f
        const val RELEASE_PER_SECOND = 1.1f
        private const val FLOOR = 0.2f
    }
}
