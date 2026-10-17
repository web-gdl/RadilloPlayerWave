package com.radillo.playerwave.player.jancar

/**
 * Estado del puente con el estéreo para el informe de diagnóstico: si el servicio existe, si se
 * conectó, si la app está registrada como fuente y qué órdenes llegaron (contadas, no una línea
 * por pulsación: el registro de eventos es pequeño).
 */
object JancarStatus {

    enum class Link(val label: String) {
        UNKNOWN("sin comprobar"),
        ABSENT("no es un estéreo Jancar"),
        CONNECTING("conectando"),
        CONNECTED("conectado"),
        LOST("conexión perdida"),
    }

    @Volatile var link: Link = Link.UNKNOWN
    @Volatile var registered: Boolean = false
    @Volatile var failedCalls: Int = 0

    private val counts = LinkedHashMap<String, Int>()

    fun count(label: String) {
        synchronized(counts) { counts[label] = (counts[label] ?: 0) + 1 }
    }

    fun summary(): String = buildString {
        append(link.label)
        if (link == Link.ABSENT || link == Link.UNKNOWN) return@buildString
        append(if (registered) " · registrada como fuente" else " · sin registrar")
        if (failedCalls > 0) append(" · llamadas fallidas $failedCalls")
        val snapshot = synchronized(counts) { counts.toList() }
        append(" · órdenes: ")
        append(if (snapshot.isEmpty()) "ninguna" else snapshot.joinToString { (k, v) -> "$k×$v" })
    }

    /** Solo para pruebas. */
    fun reset() {
        link = Link.UNKNOWN
        registered = false
        failedCalls = 0
        synchronized(counts) { counts.clear() }
    }
}
