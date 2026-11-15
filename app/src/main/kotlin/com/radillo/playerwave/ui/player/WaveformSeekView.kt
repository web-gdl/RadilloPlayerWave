package com.radillo.playerwave.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.radillo.playerwave.R
import com.radillo.playerwave.data.waveform.WaveformShape

/**
 * Barra de progreso de «Reproduciendo» dibujada como onda de sonido: una columna por tramo de la
 * canción, en color de acento lo ya reproducido y apagado lo que falta, con una línea en la posición
 * actual. Mientras la onda no está calculada (o si no se puede calcular) se ve como una barra lisa
 * gruesa con su punto. Toda la altura de la vista es zona táctil para arrastrar.
 */
class WaveformSeekView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Avisos al arrastrar: empieza, se mueve (fracción 0–1) y termina (`seek` = hay que saltar ahí). */
    interface Listener {
        fun onStart() {}
        fun onChange(fraction: Float) {}
        fun onStop(fraction: Float, seek: Boolean) {}
    }

    var listener: Listener? = null

    /** Posición 0–1. */
    var fraction: Float = 0f
        set(value) {
            val v = value.coerceIn(0f, 1f)
            if (v != field) {
                field = v
                invalidate()
            }
        }

    /** Alturas de la onda (ver [WaveformShape]); `null` = barra lisa. */
    var peaks: ByteArray? = null
        set(value) {
            if (field !== value) {
                field = value
                invalidate()
            }
        }

    var accentColor: Int = context.getColor(R.color.accent)
        set(value) {
            if (field != value) {
                field = value
                played.color = value
                invalidate()
            }
        }

    private val density = resources.displayMetrics.density
    private val played = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
    private val pending = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.seek_pending) }
    private val cursor = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.text_primary) }
    private val rect = RectF()
    private var dragging = false

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val x = w * fraction
        val wave = peaks
        if (wave == null || wave.isEmpty()) {
            // Barra lisa: pista redondeada y punto en la posición.
            val track = 8f * density
            val top = (h - track) / 2f
            val radius = track / 2f
            rect.set(0f, top, w, top + track)
            canvas.drawRoundRect(rect, radius, radius, pending)
            rect.set(0f, top, x, top + track)
            canvas.drawRoundRect(rect, radius, radius, played)
            val thumb = 13f * density
            canvas.drawCircle(x.coerceIn(thumb, w - thumb), h / 2f, thumb, played)
            return
        }
        val n = wave.size
        val slot = w / n
        val gap = (slot * 0.3f).coerceAtLeast(1f)
        val bar = (slot - gap).coerceAtLeast(1f)
        val minHeight = 4f * density
        val corner = bar / 2f
        for (i in 0 until n) {
            val left = i * slot + gap / 2f
            val barHeight = (WaveformShape.level(wave, i) * h).coerceAtLeast(minHeight)
            val top = (h - barHeight) / 2f
            rect.set(left, top, left + bar, top + barHeight)
            canvas.drawRoundRect(rect, corner, corner, if (left + bar / 2f <= x) played else pending)
        }
        val line = 3f * density
        val cx = x.coerceIn(line / 2f, w - line / 2f)
        rect.set(cx - line / 2f, 0f, cx + line / 2f, h)
        canvas.drawRoundRect(rect, line / 2f, line / 2f, cursor)
    }

    @SuppressLint("ClickableViewAccessibility") // arrastre continuo: no hay un «clic» que anunciar
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || width <= 0) return false
        val f = (event.x / width).coerceIn(0f, 1f)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                dragging = true
                listener?.onStart()
                fraction = f
                listener?.onChange(f)
            }
            MotionEvent.ACTION_MOVE -> if (dragging) {
                fraction = f
                listener?.onChange(f)
            }
            MotionEvent.ACTION_UP -> if (dragging) {
                dragging = false
                fraction = f
                listener?.onStop(f, seek = true)
            }
            MotionEvent.ACTION_CANCEL -> if (dragging) {
                dragging = false
                listener?.onStop(fraction, seek = false)
            }
        }
        return true
    }
}
