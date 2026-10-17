package com.radillo.playerwave.player.jancar

/** Orden que el servicio del estéreo envía a la fuente registrada (volante, panel, sistema). */
sealed class JancarCommand(val label: String) {
    /** Pausa temporal (llamada, asistente de voz, pantalla apagada…): volverá con [Resume]. */
    data object Suspend : JancarCommand("pausa temporal")
    data object Resume : JancarCommand("reanudar")
    data object Pause : JancarCommand("pausa")
    data object Play : JancarCommand("reproducir")
    data object PlayPause : JancarCommand("play/pausa")
    /** Otra fuente tomó el audio, o el auto se apagó. */
    data object Stop : JancarCommand("detener")
    data object Next : JancarCommand("siguiente")
    data object Previous : JancarCommand("anterior")
    /** En milisegundos: así lo pasa el propio servicio a las apps de terceros (`TransportControls.seekTo`). */
    data class SeekTo(val positionMs: Long) : JancarCommand("buscar")
    /** Atenuación (p. ej. mientras habla el navegador): 0..1. */
    data class Volume(val volume: Float) : JancarCommand("volumen")
    /** El sistema pide salir; [source] 0 = el sistema al apagar, 1 = barra de estado, 2 = voz. */
    data class Quit(val source: Int) : JancarCommand("salir")
    /** Órdenes que la app no usa (favorito, modo, filtro…): se cuentan y se ignoran. */
    data class Ignored(val name: String) : JancarCommand(name)

    /** Lector de argumentos de la transacción (un `Parcel` en el dispositivo). */
    interface Args {
        fun readInt(): Int
        fun readFloat(): Float
        fun readString(): String?
    }

    companion object {
        /**
         * Decodifica una transacción de `IMediaControlCallback`. Devuelve `null` si el código no es
         * de esta interfaz. Los argumentos se leen siempre, aunque la orden se ignore, para dejar
         * la transacción consumida tal como la escribió el servicio.
         */
        fun decode(code: Int, args: Args): JancarCommand? = when (code) {
            1 -> Suspend
            2 -> Resume
            3 -> Pause
            4 -> Play
            5 -> PlayPause
            6 -> Quit(args.readInt())
            7 -> Stop
            8 -> Volume(args.readFloat())
            9 -> Next
            10 -> Previous
            11 -> { args.readInt(); Ignored("elegir canción") }
            12 -> { args.readInt(); Ignored("favorito") }
            13 -> { args.readInt(); Ignored("permiso de video") }
            14 -> SeekTo(args.readInt().toLong().coerceAtLeast(0L))
            15 -> { args.readInt(); Ignored("modo de reproducción") }
            16 -> Ignored("aleatorio")
            17 -> { args.readString(); args.readString(); Ignored("filtro") }
            18 -> { args.readInt(); args.readInt(); Ignored("velocidad") }
            else -> null
        }
    }
}
