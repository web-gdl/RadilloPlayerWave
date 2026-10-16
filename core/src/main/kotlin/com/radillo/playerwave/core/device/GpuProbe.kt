package com.radillo.playerwave.core.device

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20

/** Datos de la GPU leídos de OpenGL ES; `null` en los campos que no se pudieron obtener. */
class GpuInfo(val renderer: String?, val vendor: String?, val version: String?)

/**
 * Crea un contexto EGL mínimo (pbuffer 1×1) en el hilo actual, lee las cadenas de la GPU y lo
 * libera. Cuesta unos milisegundos: llamarlo en un hilo de fondo y una sola vez. Nunca lanza.
 */
object GpuProbe {

    fun probe(): GpuInfo {
        var display = EGL14.EGL_NO_DISPLAY
        var context = EGL14.EGL_NO_CONTEXT
        var surface = EGL14.EGL_NO_SURFACE
        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return GpuInfo(null, null, null)
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return GpuInfo(null, null, null)

            val attribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) || count[0] == 0) {
                return GpuInfo(null, null, null)
            }
            val config = configs[0] ?: return GpuInfo(null, null, null)
            context = EGL14.eglCreateContext(
                display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
            )
            if (context == EGL14.EGL_NO_CONTEXT) return GpuInfo(null, null, null)
            surface = EGL14.eglCreatePbufferSurface(
                display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0,
            )
            if (surface == EGL14.EGL_NO_SURFACE) return GpuInfo(null, null, null)
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return GpuInfo(null, null, null)
            return GpuInfo(
                renderer = GLES20.glGetString(GLES20.GL_RENDERER),
                vendor = GLES20.glGetString(GLES20.GL_VENDOR),
                version = GLES20.glGetString(GLES20.GL_VERSION),
            )
        } catch (_: Throwable) {
            return GpuInfo(null, null, null)
        } finally {
            try {
                if (display != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                    if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                    EGL14.eglTerminate(display)
                }
            } catch (_: Throwable) {
                // Nada que hacer: solo era diagnóstico.
            }
        }
    }
}
