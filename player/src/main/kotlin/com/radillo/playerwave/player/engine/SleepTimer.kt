package com.radillo.playerwave.player.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Temporizador de apagado: pausa la reproducción cuando vence. Vive en el proceso de la app;
 * si el sistema mata el proceso, el temporizador desaparece (y con él la reproducción).
 */
@Singleton
class SleepTimer @Inject constructor(
    private val playback: PlaybackController,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    private val _remainingMs = MutableStateFlow<Long?>(null)
    /** Milisegundos restantes, o null si no hay temporizador activo. */
    val remainingMs: StateFlow<Long?> = _remainingMs

    fun start(durationMs: Long) {
        cancel()
        val end = System.currentTimeMillis() + durationMs
        _remainingMs.value = durationMs
        job = scope.launch {
            while (isActive) {
                val left = end - System.currentTimeMillis()
                if (left <= 0) break
                _remainingMs.value = left
                delay(1_000)
            }
            if (isActive) {
                playback.pause()
                _remainingMs.value = null
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _remainingMs.value = null
    }
}
