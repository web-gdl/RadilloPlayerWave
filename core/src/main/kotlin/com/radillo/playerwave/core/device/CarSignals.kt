package com.radillo.playerwave.core.device

import android.os.SystemClock

/**
 * Señales que el estéreo (servicio Jancar) anuncia a todas las apps: cámara de reversa, llave
 * (ACC) y teclas del volante. Aquí solo se guarda el estado; el receptor lo registra la app.
 *
 * - La reversa pausa el dibujo del visualizador (libera la CPU para la cámara).
 * - Las teclas y la llave se cuentan para el informe de diagnóstico: confirman en el auto que las
 *   señales llegan, sin llenar el registro con una línea por pulsación.
 */
object CarSignals {
    const val ACTION_BACKCAR_STARTED = "android.backcar.action.STARTED"
    const val ACTION_BACKCAR_FINISH = "android.backcar.action.FINISH"
    const val ACTION_ACC = "com.jancar.services.action.acc"
    const val EXTRA_ACC = "acc"
    const val ACTION_KEY_EVENT = "com.jancar.services.action.key.event"
    const val EXTRA_KEY_ID = "key_event_id"
    const val EXTRA_KEY_TYPE = "key_event_type"

    /** Si no llega el aviso de fin de la reversa, el visualizador vuelve solo pasado este tiempo. */
    const val MAX_REVERSE_MS = 5 * 60_000L

    private const val KEY_TYPE_DOWN = 1
    private const val KEY_TYPE_ONCE = 2

    /** Reloj monótono (el reloj de pared del estéreo se corrige tras arrancar); sustituible en pruebas. */
    var clock: () -> Long = { SystemClock.elapsedRealtime() }

    @Volatile private var reverseSince = 0L
    @Volatile var accOn: Boolean? = null
        private set
    private val keyCounts = LinkedHashMap<String, Int>()

    /** La cámara de reversa está en pantalla (con un tope por si se pierde el aviso de fin). */
    val reverseActive: Boolean
        get() {
            val since = reverseSince
            return since != 0L && clock() - since < MAX_REVERSE_MS
        }

    /** Devuelve `true` si el estado cambió (para registrarlo una sola vez). */
    fun onReverse(active: Boolean): Boolean {
        val was = reverseActive
        reverseSince = if (active) clock().coerceAtLeast(1L) else 0L
        return was != active
    }

    /** Lee el extra de la llave (`true`/`false` o 1/0 según el equipo); `null` si no viene. */
    fun accFrom(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is Int -> value == 1
        else -> null
    }

    /** Devuelve `true` si el estado cambió. */
    fun onAcc(on: Boolean): Boolean {
        val changed = accOn != on
        accOn = on
        // Al apagar o encender, una reversa pendiente ya no tiene sentido.
        reverseSince = 0L
        return changed
    }

    /**
     * Cuenta una tecla del volante. Solo cuentan «presionar» y «pulsación» (las que hacen actuar
     * al estéreo), no «soltar». Devuelve el nombre si es la primera vez que se ve esa tecla.
     */
    fun onKey(id: Int, type: Int): String? {
        if (type != KEY_TYPE_DOWN && type != KEY_TYPE_ONCE) return null
        val name = keyName(id)
        val first = synchronized(keyCounts) {
            val n = (keyCounts[name] ?: 0) + 1
            keyCounts[name] = n
            n == 1
        }
        return if (first) name else null
    }

    fun keyName(id: Int): String = when (id) {
        11 -> "play/pausa"
        32 -> "siguiente"
        33 -> "anterior"
        else -> "tecla $id"
    }

    fun summary(): String = buildString {
        append("llave ")
        append(
            when (accOn) {
                true -> "encendida"
                false -> "apagada"
                null -> "sin avisos"
            },
        )
        append(" · reversa ")
        append(if (reverseActive) "activa" else "inactiva")
        append(" · teclas del volante: ")
        val keys = synchronized(keyCounts) { keyCounts.toList() }
        append(if (keys.isEmpty()) "ninguna" else keys.joinToString { (k, v) -> "$k×$v" })
    }

    /** Solo para pruebas. */
    fun reset() {
        reverseSince = 0L
        accOn = null
        synchronized(keyCounts) { keyCounts.clear() }
        clock = { SystemClock.elapsedRealtime() }
    }
}
