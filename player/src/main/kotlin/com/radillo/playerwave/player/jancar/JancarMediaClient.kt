package com.radillo.playerwave.player.jancar

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import com.radillo.playerwave.core.diagnostics.EventLog
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Conexión binder con el servicio de medios del estéreo (`com.jancar.services`). Escribe y lee las
 * transacciones a mano con los códigos de [JancarProtocol]: no hace falta el SDK del fabricante.
 *
 * - Las llamadas al servicio son síncronas: se hacen en un hilo propio, en orden, nunca en el
 *   principal.
 * - Las órdenes del servicio llegan en hilos binder y se entregan en el hilo principal.
 * - Si el servicio muere, Android vuelve a conectar solo cuando reaparece.
 */
internal class JancarMediaClient(
    private val context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onConnected()
        fun onDisconnected()
        fun onCommand(command: JancarCommand)
        /** Una llamada al servicio falló ([code]: código de la transacción de `IMedia`). */
        fun onCallFailed(what: String, code: Int)
    }

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var ioThread: Thread? = null
    private val io: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "RadilloJancar").apply { isDaemon = true }.also { ioThread = it }
    }

    @Volatile private var remote: IBinder? = null
    @Volatile private var released = false
    private var bound = false

    private val deathRecipient = IBinder.DeathRecipient {
        remote = null
        main.post { if (!released) listener.onDisconnected() }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service == null) return
            remote = service
            try {
                service.linkToDeath(deathRecipient, 0)
            } catch (_: Exception) {
                // Ya murió: llegará onServiceDisconnected.
            }
            main.post { if (!released) listener.onConnected() }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remote?.let { runCatching { it.unlinkToDeath(deathRecipient, 0) } }
            remote = null
            main.post { if (!released) listener.onDisconnected() }
        }

        override fun onNullBinding(name: ComponentName?) {
            EventLog.log("Estéreo: el servicio de medios no aceptó la conexión")
        }
    }

    /** Recibe las órdenes del servicio (botones, sistema). */
    val callback: Binder = object : Binder() {
        init {
            attachInterface(null, JancarProtocol.CALLBACK_DESCRIPTOR)
        }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code < FIRST_CALL_TRANSACTION || code > LAST_CALL_TRANSACTION) {
                return super.onTransact(code, data, reply, flags)
            }
            val command = try {
                data.enforceInterface(JancarProtocol.CALLBACK_DESCRIPTOR)
                JancarCommand.decode(code, ParcelArgs(data))
            } catch (e: Exception) {
                EventLog.log("Estéreo: orden ilegible (código $code): ${e.javaClass.simpleName}")
                null
            } ?: return super.onTransact(code, data, reply, flags)
            reply?.writeNoException()
            main.post { if (!released) listener.onCommand(command) }
            return true
        }
    }

    fun bind(): Boolean {
        if (bound) return true
        bound = try {
            context.bindService(serviceIntent(), connection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            EventLog.log("Estéreo: no se pudo conectar (${e.javaClass.simpleName})")
            false
        }
        return bound
    }

    fun open(type: Int) = call("registrar", JancarProtocol.TX_OPEN) {
        writeInt(type)
        writeStrongBinder(callback)
        writeString(context.packageName)
    }

    fun close(type: Int) = call("soltar", JancarProtocol.TX_CLOSE) { writeInt(type) }

    fun setMediaState(type: Int, state: Int, positionSec: Int, durationSec: Int) =
        call("estado", JancarProtocol.TX_SET_MEDIA_STATE) {
            writeInt(type)
            writeInt(state)
            writeInt(positionSec)
            writeInt(durationSec)
        }

    /** [pixels] en RGB_565 o ARGB_8888 (el servicio lo deduce del tamaño); vacío sin carátula. */
    fun setMediaInfo(type: Int, title: String, artist: String, width: Int, height: Int, pixels: ByteArray, index: Int, total: Int) =
        call("canción", JancarProtocol.TX_SET_MEDIA_INFO) {
            writeInt(type)
            writeString(title)
            writeString(artist)
            writeInt(width)
            writeInt(height)
            writeByteArray(pixels)
            writeInt(index)
            writeInt(total)
            writeInt(0) // favorito: la app no tiene favoritos
        }

    /** Ejecuta un trabajo en el hilo del puente (p. ej. preparar la carátula antes de enviarla). */
    fun runInBackground(task: () -> Unit) {
        if (released) return
        try {
            io.execute(task)
        } catch (_: Exception) {
            // Ya cerrado.
        }
    }

    /**
     * Suelta la fuente (si se indica) y la conexión. Lo pendiente (estado, carátula) se descarta y
     * el «soltar» se envía en el acto: si quedara en cola detrás de una carátula, podría perderse y
     * el estéreo se quedaría con una fuente muerta.
     */
    fun release(closeType: Int?) {
        if (released) return
        released = true
        io.shutdownNow()
        val binder = remote
        if (closeType != null && binder != null) {
            transactWith(binder, "soltar", JancarProtocol.TX_CLOSE) { writeInt(closeType) }
        }
        remote?.let { runCatching { it.unlinkToDeath(deathRecipient, 0) } }
        remote = null
        if (bound) {
            runCatching { context.unbindService(connection) }
            bound = false
        }
    }

    private fun call(what: String, code: Int, write: Parcel.() -> Unit) {
        if (released) return
        val task = Runnable {
            val binder = remote
            val ok = binder != null && transactWith(binder, what, code, write)
            if (!ok) main.post { if (!released) listener.onCallFailed(what, code) }
        }
        // Desde el propio hilo del puente (p. ej. tras preparar la carátula) se llama en el acto,
        // para conservar el orden respecto de lo que ya estaba en cola.
        if (Thread.currentThread() === ioThread) {
            task.run()
            return
        }
        try {
            io.execute(task)
        } catch (_: Exception) {
            // Ejecutor cerrado: la app se está cerrando.
        }
    }

    /** Devuelve `false` si la llamada falló o el servicio no la reconoce. */
    private fun transactWith(binder: IBinder, what: String, code: Int, write: Parcel.() -> Unit): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(JancarProtocol.MEDIA_DESCRIPTOR)
            data.write()
            if (!binder.transact(code, data, reply, 0)) {
                // Un firmware sin ese código devuelve false con la respuesta vacía.
                logOnce(what, "Estéreo: el servicio no reconoce «$what»")
                return false
            }
            reply.readException()
            true
        } catch (e: Exception) {
            logOnce(what, "Estéreo: falló «$what» (${e.javaClass.simpleName})")
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /** El estado se envía cada segundo: un fallo repetido no debe llenar el registro de eventos. */
    private val loggedFailures = java.util.Collections.synchronizedSet(HashSet<String>())

    private fun logOnce(what: String, message: String) {
        if (loggedFailures.add(what)) EventLog.log(message)
    }

    private class ParcelArgs(private val parcel: Parcel) : JancarCommand.Args {
        override fun readInt(): Int = parcel.readInt()
        override fun readFloat(): Float = parcel.readFloat()
        override fun readString(): String? = parcel.readString()
    }

    companion object {
        fun serviceIntent(): Intent = Intent(JancarProtocol.ACTION_MEDIA).setPackage(JancarProtocol.SERVICE_PACKAGE)

        /** `true` si el estéreo tiene el servicio (si no, la app sigue como en cualquier Android). */
        fun isServiceInstalled(context: Context): Boolean = try {
            context.packageManager.resolveService(serviceIntent(), 0) != null
        } catch (_: Exception) {
            false
        }
    }
}
