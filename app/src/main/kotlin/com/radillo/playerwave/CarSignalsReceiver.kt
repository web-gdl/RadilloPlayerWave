package com.radillo.playerwave

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.radillo.playerwave.core.device.CarSignals
import com.radillo.playerwave.core.diagnostics.EventLog

/**
 * Escucha los avisos que el estéreo manda a todas las apps (reversa, llave, teclas del volante) y
 * los anota en [CarSignals]. En equipos que no son Jancar simplemente nunca llega nada.
 */
class CarSignalsReceiver private constructor() : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            CarSignals.ACTION_BACKCAR_STARTED ->
                if (CarSignals.onReverse(true)) EventLog.log("Cámara de reversa: el visualizador se pausa")
            CarSignals.ACTION_BACKCAR_FINISH ->
                if (CarSignals.onReverse(false)) EventLog.log("Cámara de reversa terminada")
            CarSignals.ACTION_ACC -> {
                @Suppress("DEPRECATION")
                val on = when (val v = intent.extras?.get(CarSignals.EXTRA_ACC)) {
                    is Boolean -> v
                    is Int -> v == 1
                    else -> return
                }
                if (CarSignals.onAcc(on)) EventLog.log(if (on) "Llave: encendido" else "Llave: apagado")
            }
            CarSignals.ACTION_KEY_EVENT -> {
                val id = intent.getIntExtra(CarSignals.EXTRA_KEY_ID, -1)
                val type = intent.getIntExtra(CarSignals.EXTRA_KEY_TYPE, -1)
                CarSignals.onKey(id, type)?.let { EventLog.log("Tecla del volante recibida: $it") }
            }
        }
    }

    companion object {
        /** Una sola vez por proceso (desde `Application.onCreate`). */
        fun register(context: Context) {
            val filter = IntentFilter().apply {
                addAction(CarSignals.ACTION_BACKCAR_STARTED)
                addAction(CarSignals.ACTION_BACKCAR_FINISH)
                addAction(CarSignals.ACTION_ACC)
                addAction(CarSignals.ACTION_KEY_EVENT)
            }
            try {
                // Exportado: los envía otra app (el servicio del sistema).
                ContextCompat.registerReceiver(context, CarSignalsReceiver(), filter, ContextCompat.RECEIVER_EXPORTED)
            } catch (_: Exception) {
                // Sin estos avisos la app funciona igual.
            }
        }
    }
}
