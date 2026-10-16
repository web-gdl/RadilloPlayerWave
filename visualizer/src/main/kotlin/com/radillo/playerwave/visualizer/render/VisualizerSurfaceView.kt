package com.radillo.playerwave.visualizer.render

import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.os.Process
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.radillo.playerwave.core.device.RealApiLevel
import com.radillo.playerwave.visualizer.engine.VisualizerEngine

/**
 * Superficie del visualizador: un `SurfaceView` con su propio hilo de render.
 *
 * - El hilo dibuja con `lockHardwareCanvas` (GPU, API 26+ real) y cae a `lockCanvas` (software)
 *   si la unidad no lo admite. Un frame lento del visualizador nunca bloquea el hilo principal.
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
    private var engineAttached = false

    init {
        holder.addCallback(this)
    }

    /** Motor a renderizar. Puede cambiarse en caliente; el hilo se reinicia con el nuevo. */
    fun setEngine(engine: VisualizerEngine) {
        if (this.engine === engine) return
        val previous = this.engine
        if (surfaceReady && engineAttached) {
            stopThread()
            previous?.detach()
            engineAttached = false
        }
        this.engine = engine
        if (surfaceReady && visibility == VISIBLE) {
            engine.attach()
            engineAttached = true
            engine.onResize(width, height)
            startThread()
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        // Cuando la vista se oculta, libera la superficie y el hilo; cuando se muestra, los reactiva.
        if (visibility == VISIBLE) {
            if (surfaceReady && !engineAttached) {
                engine?.attach()
                startThread()
                engineAttached = true
            }
        } else {
            if (engineAttached) {
                stopThread()
                engine?.detach()
                engineAttached = false
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        if (visibility == VISIBLE) {
            engine?.attach()
            engineAttached = true
            startThread()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        engine?.onResize(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopThread()
        if (engineAttached) {
            engine?.detach()
            engineAttached = false
        }
        surfaceReady = false
    }

    private fun startThread() {
        val engine = engine ?: return
        if (thread != null) return
        thread = RenderThread(holder, engine).also { it.start() }
    }

    private fun stopThread() {
        val t = thread ?: return
        thread = null
        t.running = false
        t.interrupt()
        try {
            t.join(1_000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /** Bucle de render. Termina solo: `running = false` + `interrupt()` y `join()`. */
    private class RenderThread(
        private val holder: SurfaceHolder,
        private val engine: VisualizerEngine,
    ) : Thread("RadilloVisualizer") {

        @Volatile var running = true
        private var useHardware = Build.VERSION.SDK_INT >= 26 && RealApiLevel.effective >= 26

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

    private companion object {
        const val TAG = "VisualizerSurface"
    }
}
