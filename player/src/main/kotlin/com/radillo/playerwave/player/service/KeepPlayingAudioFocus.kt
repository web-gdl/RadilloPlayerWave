package com.radillo.playerwave.player.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.media3.common.Player
import com.radillo.playerwave.core.device.RealApiLevel
import com.radillo.playerwave.core.diagnostics.EventLog

/**
 * Foco de audio «de coche»: se pide al empezar a sonar (para que la radio u otra app se calle),
 * pero una pérdida **permanente** no pausa la música. Las pérdidas breves sí se respetan: la
 * navegación o una llamada pausan (o bajan el volumen) y la música vuelve al terminar.
 *
 * Se usa en lugar de `handleAudioFocus = true` de ExoPlayer cuando el ajuste «Seguir sonando
 * aunque otra app pida el audio» está activo. Todo ocurre en el hilo principal del servicio.
 */
internal class KeepPlayingAudioFocus(context: Context, private val player: Player) : AudioManager.OnAudioFocusChangeListener {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val useRequestApi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && RealApiLevel.effective >= 26
    private var request: Any? = null
    private var hasFocus = false
    private var pausedByTransientLoss = false
    private var ducked = false

    var enabled = false
        set(value) {
            if (field == value) return
            field = value
            if (value) {
                if (player.playWhenReady) requestFocus()
            } else {
                restoreVolume()
                abandonFocus()
                pausedByTransientLoss = false
            }
        }

    /** Llamar en cada cambio de `playWhenReady`. */
    fun onPlayWhenReadyChanged(playWhenReady: Boolean) {
        if (!enabled) return
        if (playWhenReady) {
            pausedByTransientLoss = false
            requestFocus()
        } else if (!pausedByTransientLoss) {
            abandonFocus()
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        if (!enabled) return
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                restoreVolume()
                if (pausedByTransientLoss) {
                    pausedByTransientLoss = false
                    player.play()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (player.playWhenReady) {
                    pausedByTransientLoss = true
                    player.pause()
                    EventLog.log("Pausa breve: otra app pidió el audio un momento (se reanudará)")
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                ducked = true
                player.volume = DUCK_VOLUME
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasFocus = false
                EventLog.log("Otra app se quedó el audio; la música sigue sonando (ajuste de coche)")
            }
        }
    }

    private fun requestFocus() {
        if (hasFocus) return
        val result = if (useRequestApi && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val r = (request as? AudioFocusRequest) ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(this)
                .build()
                .also { request = it }
            audioManager.requestAudioFocus(r)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        if (useRequestApi && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (request as? AudioFocusRequest)?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(this)
        }
        hasFocus = false
    }

    private fun restoreVolume() {
        if (ducked) {
            ducked = false
            player.volume = 1f
        }
    }

    fun release() {
        enabled = false
    }

    private companion object {
        const val DUCK_VOLUME = 0.3f
    }
}
