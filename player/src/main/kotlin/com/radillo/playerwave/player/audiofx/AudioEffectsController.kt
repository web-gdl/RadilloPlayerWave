package com.radillo.playerwave.player.audiofx

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log
import com.radillo.playerwave.core.model.AudioEffectsSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Banda del ecualizador tal y como la expone el dispositivo. */
data class EqBand(val index: Int, val centerHz: Int, val levelMb: Int)

/**
 * Estado observable de los efectos. `available` es `false` cuando el dispositivo no ofrece un
 * `Equalizer` operativo: la UI muestra entonces un aviso en vez de controles sin efecto.
 */
data class AudioEffectsState(
    val available: Boolean = false,
    /** `true` una vez comprobada la disponibilidad en la sesión de audio actual. */
    val checked: Boolean = false,
    val enabled: Boolean = false,
    val bands: List<EqBand> = emptyList(),
    val minLevelMb: Int = -1500,
    val maxLevelMb: Int = 1500,
    val presets: List<String> = emptyList(),
    val presetIndex: Int = -1,
    val bassBoostSupported: Boolean = false,
    val bassBoostStrength: Int = 0,
)

/**
 * Ecualizador y refuerzo de graves de la plataforma (`android.media.audiofx`), ligados al
 * `audioSessionId` del ExoPlayer. Verificable: si la creación del efecto falla o no expone
 * bandas, se marca como no disponible y no se simula nada.
 *
 * Los efectos se (re)crean cuando cambia la sesión de audio o se activan/desactivan; los
 * ajustes deseados se guardan en [desired] y se reaplican a cada efecto nuevo. Todas las
 * llamadas se hacen en el hilo principal.
 */
@Singleton
class AudioEffectsController @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var sessionId = 0
    private var desired = AudioEffectsSettings()

    private val _state = MutableStateFlow(AudioEffectsState())
    val state: StateFlow<AudioEffectsState> = _state

    init {
        scope.launch {
            AudioEffectsHub.sessionId.collect { id ->
                if (id != sessionId) {
                    sessionId = id
                    rebuild()
                }
            }
        }
    }

    /** Ajustes efectivos actuales (para persistirlos). */
    fun currentSettings(): AudioEffectsSettings = desired

    /** Aplica un conjunto completo de ajustes (p. ej. los persistidos al arrancar). */
    fun apply(settings: AudioEffectsSettings) {
        desired = settings
        rebuild()
    }

    fun setEnabled(enabled: Boolean) {
        if (desired.enabled == enabled) return
        desired = desired.copy(enabled = enabled)
        rebuild()
    }

    fun setBandLevel(band: Int, levelMb: Int) {
        val levels = currentLevels().toMutableList()
        if (band !in levels.indices) return
        val clamped = levelMb.coerceIn(_state.value.minLevelMb, _state.value.maxLevelMb)
        levels[band] = clamped
        desired = desired.copy(bandLevelsMb = levels, presetIndex = -1)
        val eq = equalizer
        if (eq == null) {
            rebuild()
        } else {
            runCatching { eq.setBandLevel(band.toShort(), clamped.toShort()) }
                .onFailure { Log.w(TAG, "setBandLevel falló", it) }
            publish()
        }
    }

    fun usePreset(index: Int) {
        val eq = equalizer
        if (eq == null) {
            desired = desired.copy(presetIndex = index, bandLevelsMb = emptyList())
            rebuild()
            return
        }
        if (index !in 0 until eq.numberOfPresets) return
        runCatching { eq.usePreset(index.toShort()) }.onFailure { return }
        val levels = (0 until eq.numberOfBands).map { eq.getBandLevel(it.toShort()).toInt() }
        desired = desired.copy(bandLevelsMb = levels, presetIndex = index)
        publish()
    }

    fun setBassBoost(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        desired = desired.copy(bassBoostStrength = clamped)
        val bb = bassBoost
        if (bb == null) {
            rebuild()
        } else {
            runCatching {
                bb.setStrength(clamped.toShort())
                bb.setEnabled(clamped > 0)
            }.onFailure { Log.w(TAG, "setStrength falló", it) }
            publish()
        }
    }

    private fun currentLevels(): List<Int> {
        val bands = _state.value.bands
        if (bands.isNotEmpty() && desired.bandLevelsMb.size == bands.size) return desired.bandLevelsMb
        return bands.map { it.levelMb }
    }

    private fun rebuild() {
        release()
        if (sessionId == 0) {
            _state.value = AudioEffectsState(checked = false)
            return
        }
        if (!desired.enabled) {
            // Sin efectos activos no se deja nada creado, pero se comprueba la disponibilidad.
            _state.value = probe().copy(enabled = false)
            return
        }
        val eq = try {
            Equalizer(0, sessionId)
        } catch (e: Exception) {
            Log.w(TAG, "Equalizer no disponible en la sesión $sessionId", e)
            _state.value = AudioEffectsState(available = false, checked = true)
            return
        }
        val bandCount = runCatching { eq.numberOfBands.toInt() }.getOrDefault(0)
        if (bandCount <= 0) {
            runCatching { eq.release() }
            _state.value = AudioEffectsState(available = false, checked = true)
            return
        }
        equalizer = eq
        try {
            eq.setEnabled(true)
            if (desired.bandLevelsMb.size == bandCount) {
                val range = eq.bandLevelRange
                desired.bandLevelsMb.forEachIndexed { i, mb ->
                    eq.setBandLevel(i.toShort(), mb.coerceIn(range[0].toInt(), range[1].toInt()).toShort())
                }
            } else if (desired.presetIndex in 0 until eq.numberOfPresets) {
                eq.usePreset(desired.presetIndex.toShort())
                desired = desired.copy(
                    bandLevelsMb = (0 until bandCount).map { eq.getBandLevel(it.toShort()).toInt() },
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron aplicar los niveles del ecualizador", e)
        }
        bassBoost = createBassBoost()
        publish()
    }

    /** Refuerzo de graves con la fuerza deseada, o `null` si no hay; si falla al ajustarlo, se libera. */
    private fun createBassBoost(): BassBoost? {
        val bb = try {
            BassBoost(0, sessionId)
        } catch (e: Exception) {
            return null
        }
        return try {
            if (bb.strengthSupported) {
                bb.setStrength(desired.bassBoostStrength.coerceIn(0, 1000).toShort())
                bb.setEnabled(desired.bassBoostStrength > 0)
                bb
            } else {
                bb.release()
                null
            }
        } catch (e: Exception) {
            runCatching { bb.release() }
            null
        }
    }

    /** Comprueba la disponibilidad sin dejar el efecto creado. */
    private fun probe(): AudioEffectsState {
        return try {
            val eq = Equalizer(0, sessionId)
            // Si leer el efecto falla, se libera igual: si no, el efecto nativo queda vivo hasta el GC.
            try {
                snapshot(eq, null)
            } finally {
                runCatching { eq.release() }
            }
        } catch (e: Exception) {
            AudioEffectsState(available = false, checked = true)
        }
    }

    private fun publish() {
        val eq = equalizer ?: return
        // Leer el efecto puede fallar (p. ej. otra app tomó su control): se conserva el último estado.
        try {
            _state.value = snapshot(eq, bassBoost)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo leer el estado del ecualizador", e)
        }
    }

    private fun snapshot(eq: Equalizer, bb: BassBoost?): AudioEffectsState {
        val range = eq.bandLevelRange
        val bands = (0 until eq.numberOfBands).map { i ->
            val index = i.toShort()
            EqBand(index = i, centerHz = eq.getCenterFreq(index) / 1000, levelMb = eq.getBandLevel(index).toInt())
        }
        val presets = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
        return AudioEffectsState(
            available = bands.isNotEmpty(),
            checked = true,
            enabled = desired.enabled,
            bands = bands,
            minLevelMb = range[0].toInt(),
            maxLevelMb = range[1].toInt(),
            presets = presets,
            presetIndex = desired.presetIndex,
            bassBoostSupported = bb?.strengthSupported ?: false,
            bassBoostStrength = desired.bassBoostStrength,
        )
    }

    private fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        equalizer = null
        bassBoost = null
    }

    private companion object {
        const val TAG = "AudioEffects"
    }
}
