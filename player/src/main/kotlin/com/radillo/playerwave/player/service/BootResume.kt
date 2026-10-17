package com.radillo.playerwave.player.service

import android.os.Handler
import android.os.Looper

/**
 * Aviso de la actividad al servicio de reproducción: «el estéreo acaba de arrancar y abrió la app
 * como app de memoria; si la música sonaba, que siga». La actividad puede llegar antes de que el
 * servicio exista: entonces el aviso queda pendiente hasta que se cree (mismo proceso).
 */
object BootResume {
    private val main = Handler(Looper.getMainLooper())
    private var pending = false
    private var target: (() -> Unit)? = null

    /** Desde cualquier hilo. */
    fun request() {
        main.post {
            val t = target
            if (t != null) t() else pending = true
        }
    }

    internal fun attach(onRequest: () -> Unit) {
        target = onRequest
        if (pending) {
            pending = false
            onRequest()
        }
    }

    internal fun detach() {
        target = null
    }
}
