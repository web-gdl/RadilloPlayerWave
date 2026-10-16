package com.radillo.playerwave.player.audiofx

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * El servicio publica aquí el `audioSessionId` del ExoPlayer; el [AudioEffectsController]
 * liga a él los efectos de la plataforma. Único por proceso, como el reproductor.
 */
object AudioEffectsHub {
    private val _sessionId = MutableStateFlow(0)
    /** 0 mientras no hay sesión de audio. */
    val sessionId: StateFlow<Int> = _sessionId

    fun onSessionId(id: Int) {
        _sessionId.value = id
    }
}
