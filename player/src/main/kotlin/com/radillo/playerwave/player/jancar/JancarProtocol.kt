package com.radillo.playerwave.player.jancar

/**
 * Lo que hace falta saber del servicio de medios de los estéreos Jancar (alps 8227L y similares)
 * para que RadilloPlayerWave se registre como fuente de audio. Son los nombres públicos de su
 * interfaz binder (AIDL estándar): el código que habla con ella es propio, no del fabricante.
 *
 * Detalle del análisis y de por qué se usa cada cosa: `docs/TARGET_DEVICE.md`.
 */
object JancarProtocol {
    const val SERVICE_PACKAGE = "com.jancar.services"
    const val ACTION_MEDIA = "com.jancar.services.action.media"

    const val MEDIA_DESCRIPTOR = "com.jancar.services.media.IMedia"
    const val CALLBACK_DESCRIPTOR = "com.jancar.services.media.IMediaControlCallback"

    // ---- IMedia (llamadas de la app al servicio) ----
    const val TX_OPEN = 1
    const val TX_CLOSE = 2
    const val TX_SET_MEDIA_INFO = 8
    const val TX_SET_MEDIA_STATE = 9

    /**
     * Tipo de fuente con el que se registra la app: «de terceros». No se usa MÚSICA (0) aunque el
     * lanzador solo pinte la carátula de esa: si la app de fábrica se abre con el mismo tipo, el
     * servicio cambia de dueño sin avisar al anterior y sonarían las dos a la vez. Con un tipo
     * distinto, el servicio detiene a la otra fuente al cambiar.
     */
    const val TYPE_THIRD_PARTY = 20

    const val STATE_STOPPED = 1
    const val STATE_PAUSED = 2
    const val STATE_PLAYING = 3

    /** Origen de `quitApp`: 0 = el sistema (p. ej. al apagar el auto), 1 = barra de estado, 2 = voz. */
    const val QUIT_SYSTEM = 0

    /** La carátula se envía como píxeles crudos y el servicio la reduce a 240 px: no mandar más. */
    const val ART_MAX_PX = 240

    /** Extra con el que el sistema abre la «app de memoria» al arrancar el estéreo. */
    const val EXTRA_BOOT_MEMORY = "boot_memory"

    /**
     * Estado que se publica según el reproductor. Una cola restaurada que aún no se preparó cuenta
     * como «en pausa» (hay canción cargada); terminada, vacía o con error (p. ej. se quitó la USB),
     * como «detenida».
     */
    fun stateFor(playWhenReady: Boolean, ended: Boolean, hasItems: Boolean, failed: Boolean = false): Int = when {
        !hasItems || ended || failed -> STATE_STOPPED
        playWhenReady -> STATE_PLAYING
        else -> STATE_PAUSED
    }
}
