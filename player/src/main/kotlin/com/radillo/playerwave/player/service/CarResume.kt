package com.radillo.playerwave.player.service

import android.content.Context
import com.radillo.playerwave.player.jancar.JancarBridge
import com.radillo.playerwave.player.jancar.JancarMediaClient

/**
 * Respaldo para seguir con la música al volver a encender el auto cuando la app ya no estaba
 * abierta (el estéreo se reinició o el sistema la cerró durante el apagado): el receptor de
 * arranque y de la llave de la app pregunta aquí si debe reabrirla como «app de memoria».
 */
object CarResume {
    @Volatile internal var serviceRunning = false

    /**
     * `true` si es un estéreo Jancar, la app no está corriendo y el usuario tenía la música
     * sonando cuando se apagó el auto (una pausa suya no cuenta).
     */
    fun shouldRelaunch(context: Context): Boolean =
        !serviceRunning && JancarMediaClient.isServiceInstalled(context) && JancarBridge.userWantsPlayback(context)
}
