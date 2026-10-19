package com.radillo.playerwave

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.radillo.playerwave.core.device.CarSignals
import com.radillo.playerwave.core.diagnostics.EventLog
import com.radillo.playerwave.player.jancar.JancarProtocol
import com.radillo.playerwave.player.service.CarResume

/**
 * Respaldo de la «app de memoria» del estéreo: si al encender (arranque del equipo o llave) la app
 * ya no estaba corriendo y la música sonaba cuando se apagó el auto, se reabre como lo haría el
 * estéreo (`boot_memory=true`) y la música sigue donde iba.
 *
 * El estéreo hace lo mismo solo cuando arranca su escritorio de fábrica; con otro escritorio o si
 * el equipo solo despertó, no lo hacía y la app quedaba cerrada.
 */
class PowerOnReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == CarSignals.ACTION_ACC) {
            @Suppress("DEPRECATION")
            if (CarSignals.accFrom(intent.extras?.get(CarSignals.EXTRA_ACC)) != true) return
        }
        if (!CarResume.shouldRelaunch(context)) return
        EventLog.log("Encendido (${action.substringAfterLast('.')}): la app no estaba abierta; se reabre para seguir con la música")
        val launch = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(JancarProtocol.EXTRA_BOOT_MEMORY, "true")
        try {
            context.startActivity(launch)
        } catch (e: Exception) {
            EventLog.log("No se pudo reabrir la app al encender (${e.javaClass.simpleName})")
        }
    }
}
