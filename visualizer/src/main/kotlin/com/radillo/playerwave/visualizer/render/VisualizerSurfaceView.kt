package com.radillo.playerwave.visualizer.render

import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.radillo.playerwave.core.diagnostics.EventLog
import com.radillo.playerwave.core.diagnostics.RenderMode
import com.radillo.playerwave.visualizer.engine.VisualizerEngine

/**
 * Superficie del visualizador: un `SurfaceView` con su propio hilo de render.
 *
 * - El hilo dibuja con la CPU (`lockCanvas`) o con la GPU (`lockHardwareCanvas`) según
 *   [RenderMode]: en equipos de gama baja con Android 8–9 real la GPU puede cerrar la app al
 *   destruir la superficie. Un frame lento del visualizador nunca bloquea el hilo principal.
 * - Al destruirse la superficie, el hilo se detiene y se espera a que termine **antes** de
 *   devolver el control: después de `surfaceDestroyed` nadie puede tocar la superficie.
 * - El tope de fps del motor se respeta espaciando los frames (`minFrameIntervalNanos`); sin tope,
 *   la cola de búferes de la superficie limita el bucle a la cadencia de la pantalla.
 * - El análisis de audio se enciende con la superficie y se apaga con ella ([VisualizerEngine.attach]/
 *   [VisualizerEngine.detach]), de modo que en segundo plano no se gasta CPU.
 */
class VisualizerSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private var engine: VisualizerEngine? = null
    private var thread: RenderThread? = null
    private var surfaceReady = false

    init {
        holder.addCallback(this)
    }

    /** Motor a renderizar. Puede cambiarse en caliente; el hilo se reinicia con el nuevo. */
    fun setEngine(engine: VisualizerEngine) {
        if (this.engine === engine) return
        val previous = this.engine
        if (surfaceReady) {
            stopThread()
            previous?.detach()
        }
        this.engine = engine
        if (surfaceReady) {
            engine.attach()
            engine.onResize(width, height)
            startThread()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (surfaceReady) return
        surfaceReady = true
        engine?.attach()
        startThread()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        engine?.onResize(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        val gpu = thread?.usesGpu == true
        val token = if (gpu) RenderMode.beginGpuTransition() else null
        stopThread()
        if (surfaceReady) engine?.detach()
        surfaceReady = false
        // La superficie se libera al volver de aquí; si el proceso muere en ese instante, la marca
        // queda y el siguiente arranque usará la CPU.
        if (token != null) mainHandler.postDelayed({ RenderMode.endGpuTransition(token) }, TRANSITION_GRACE_MS)
    }

    private fun startThread() {
        val engine = engine ?: return
        if (thread != null) return
        val gpu = RenderMode.useGpu() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        val token = if (gpu) RenderMode.beginGpuTransition() else null
        thread = RenderThread(holder, engine, gpu) {
            if (token != null) mainHandler.postDelayed({ RenderMode.endGpuTransition(token) }, TRANSITION_GRACE_MS)
        }.also { it.start() }
    }

    private fun stopThread() {
        val t = thread ?: return
        thread = null
        t.running = false
        t.interrupt()
        try {
            // Hay que esperar a que termine: si siguiera dibujando tras liberar la superficie, el
            // cierre sería nativo. Un frame tarda milisegundos; el límite solo evita un bloqueo eterno.
            t.join(STOP_TIMEOUT_MS)
            if (t.isAlive) EventLog.log("El hilo del visualizador no terminó a tiempo")
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /** Bucle de render. Termina solo: `running = false` + `interrupt()` y `join()`. */
    private class RenderThread(
        private val holder: SurfaceHolder,
        private val engine: VisualizerEngine,
        gpu: Boolean,
        private val onFirstFrame: () -> Unit,
    ) : Thread("RadilloVisualizer") {

        @Volatile var running = true
        @Volatile private var useHardware = gpu
        val usesGpu: Boolean get() = useHardware
        private var firstFramePosted = false

        override fun run() {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY)
            } catch (_: Throwable) {
                // Sin permiso para cambiar la prioridad: se sigue con la normal.
            }
            var lastFrameStart = 0L
            while (running) {
                val interval = engine.minFrameIntervalNanos
                val now = System.nanoTime()
                if (interval > 0L && lastFrameStart != 0L) {
                    val wait = lastFrameStart + interval - now
                    if (wait > 0L) {
                        if (!sleepNanos(wait)) break
                        continue
                    }
                }
                val canvas = lockCanvas()
                if (canvas == null) {
                    // Superficie no disponible todavía (o ya destruida): esperar un poco.
                    if (!sleepNanos(4_000_000L)) break
                    continue
                }
                lastFrameStart = System.nanoTime()
                try {
                    engine.render(canvas, lastFrameStart)
                } catch (t: Throwable) {
                    Log.e(TAG, "Fallo al dibujar un frame", t)
                } finally {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (t: Throwable) {
                        Log.w(TAG, "No se pudo publicar el frame", t)
                    }
                }
                if (!firstFramePosted) {
                    firstFramePosted = true
                    onFirstFrame()
                }
            }
        }

        private fun lockCanvas(): Canvas? {
            // La comprobación de SDK_INT se repite aquí para que lint la reconozca (NewApi).
            if (useHardware && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    return holder.lockHardwareCanvas()
                } catch (t: Throwable) {
                    // NoSuchMethodError en ROMs que fingen la versión, o fallo del compositor: a software.
                    Log.w(TAG, "lockHardwareCanvas no disponible; se usa render por software", t)
                    useHardware = false
                }
            }
            return try {
                holder.lockCanvas()
            } catch (t: Throwable) {
                null
            }
        }

        /** Duerme `nanos`; devuelve `false` si el hilo debe terminar. */
        private fun sleepNanos(nanos: Long): Boolean {
            if (!running) return false
            try {
                val ms = nanos / 1_000_000L
                val rest = (nanos % 1_000_000L).toInt()
                sleep(ms, rest)
            } catch (_: InterruptedException) {
                return running
            }
            return running
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private companion object {
        const val TAG = "VisualizerSurface"
        const val STOP_TIMEOUT_MS = 3_000L
        const val TRANSITION_GRACE_MS = 1_500L
    }
}
