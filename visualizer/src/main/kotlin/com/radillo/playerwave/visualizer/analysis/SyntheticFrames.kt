package com.radillo.playerwave.visualizer.analysis

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Genera frames de respaldo cuando no hay datos de audio fiables:
 * - en pausa o silencio: el frame anterior decae suavemente hacia cero;
 * - reproduciendo sin datos (ruta de audio sin PCM): movimiento ambiental lento, claramente
 *   marcado como `synthetic`, para que las escenas no se queden congeladas.
 */
class SyntheticFrameSource(private val config: AnalyzerConfig) {

    private var phase = 0f

    /** Frame con todos los valores a cero (estado inicial). */
    fun silent(): AudioFrame =
        AudioFrame.silent(config.spectrumSize, config.bandCount, config.waveformSize, synthetic = true)

    /** Copia decaída de `previous`: los valores se multiplican por e^(−dt/τ). */
    fun decay(previous: AudioFrame, dtSeconds: Float, tauSeconds: Float = 0.18f): AudioFrame {
        val k = exp(-dtSeconds / tauSeconds)
        return AudioFrame(
            spectrum = scaled(previous.spectrum, k),
            bands = scaled(previous.bands, k),
            bass = previous.bass * k,
            mid = previous.mid * k,
            treble = previous.treble * k,
            rms = previous.rms * k,
            peak = previous.peak * k,
            onset = 0f,
            waveformLeft = scaled(previous.waveformLeft, k),
            waveformRight = scaled(previous.waveformRight, k),
            timestampUs = previous.timestampUs,
            synthetic = true,
        )
    }

    /** Movimiento ambiental lento (sin música real). */
    fun ambient(dtSeconds: Float, timestampUs: Long): AudioFrame {
        phase += dtSeconds * 0.6f
        val bands = FloatArray(config.bandCount) { i ->
            val x = i.toFloat() / config.bandCount
            val wave = 0.5f + 0.5f * sin((phase * 2f * PI + x * 7f).toFloat())
            (0.18f + 0.22f * wave * (1f - x * 0.6f)).coerceIn(0f, 1f)
        }
        val spectrum = FloatArray(config.spectrumSize) { k ->
            val x = k.toFloat() / config.spectrumSize
            (0.15f * exp(-x * 6f) * (0.6f + 0.4f * sin((phase * 3f + x * 40f).toFloat())))
        }
        val wave = FloatArray(config.waveformSize) { i ->
            val t = i.toFloat() / config.waveformSize
            0.25f * sin((t * 2f * PI * 2f + phase * 4f).toFloat()) * sin((phase * 0.7f).toFloat())
        }
        val slow = 0.5f + 0.5f * sin((phase * 2f * PI * 0.5f).toFloat())
        return AudioFrame(
            spectrum = spectrum,
            bands = bands,
            bass = 0.25f + 0.2f * slow,
            mid = 0.2f + 0.15f * (1f - slow),
            treble = 0.12f + 0.1f * slow,
            rms = 0.2f + 0.1f * slow,
            peak = 0.3f,
            onset = 0f,
            waveformLeft = wave,
            waveformRight = wave,
            timestampUs = timestampUs,
            synthetic = true,
        )
    }

    private fun scaled(src: FloatArray, k: Float): FloatArray = FloatArray(src.size) { src[it] * k }
}
