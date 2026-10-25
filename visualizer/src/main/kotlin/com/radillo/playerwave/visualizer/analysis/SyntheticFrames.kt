package com.radillo.playerwave.visualizer.analysis

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Genera frames de respaldo cuando no hay datos de audio fiables:
 * - en pausa o silencio: el frame anterior decae suavemente hacia cero;
 * - reproduciendo sin datos (ruta de audio sin PCM): movimiento ambiental lento, claramente
 *   marcado como `synthetic`, para que las escenas no se queden congeladas.
 *
 * El motor pide uno de estos frames en cada frame de render (pausa o respaldo), así que sus
 * arrays no se asignan cada vez: [decay] y [ambient] escriben en dos juegos de búferes que se
 * alternan. Un frame devuelto sigue intacto hasta la segunda llamada siguiente; el motor solo
 * conserva el último y las escenas lo leen dentro del mismo frame. [silent] sí asigna siempre.
 * Las llamadas deben hacerse desde un único hilo.
 */
class SyntheticFrameSource(private val config: AnalyzerConfig) {

    private class Buffers(config: AnalyzerConfig) {
        val spectrum = FloatArray(config.spectrumSize)
        val bands = FloatArray(config.bandCount)
        val waveformLeft = FloatArray(config.waveformSize)
        val waveformRight = FloatArray(config.waveformSize)
    }

    private val buffers = arrayOf(Buffers(config), Buffers(config))
    private var nextBuffers = 0

    /** Envolvente fija del espectro ambiental: 0.15·e^(−6x), igual en cada frame. */
    private val spectrumEnvelope = FloatArray(config.spectrumSize) { k ->
        val x = k.toFloat() / config.spectrumSize
        0.15f * exp(-x * 6f)
    }

    private var phase = 0f

    private fun takeBuffers(): Buffers {
        val b = buffers[nextBuffers]
        nextBuffers = nextBuffers xor 1
        return b
    }

    /** Frame con todos los valores a cero (estado inicial). */
    fun silent(): AudioFrame =
        AudioFrame.silent(config.spectrumSize, config.bandCount, config.waveformSize, synthetic = true)

    /** Copia decaída de `previous`: los valores se multiplican por e^(−dt/τ). */
    fun decay(previous: AudioFrame, dtSeconds: Float, tauSeconds: Float = 0.18f): AudioFrame {
        val k = exp(-dtSeconds / tauSeconds)
        val b = takeBuffers()
        val left = scaled(previous.waveformLeft, k, b.waveformLeft)
        // Si L y R comparten array (mono o frame ambiental), se conserva: escalarlo dos veces
        // sobre el mismo búfer lo atenuaría el doble.
        val right = if (previous.waveformRight === previous.waveformLeft) left else scaled(previous.waveformRight, k, b.waveformRight)
        return AudioFrame(
            spectrum = scaled(previous.spectrum, k, b.spectrum),
            bands = scaled(previous.bands, k, b.bands),
            bass = previous.bass * k,
            mid = previous.mid * k,
            treble = previous.treble * k,
            rms = previous.rms * k,
            peak = previous.peak * k,
            onset = 0f,
            waveformLeft = left,
            waveformRight = right,
            timestampUs = previous.timestampUs,
            synthetic = true,
        )
    }

    /** Movimiento ambiental lento (sin música real). */
    fun ambient(dtSeconds: Float, timestampUs: Long): AudioFrame {
        phase += dtSeconds * 0.6f
        val b = takeBuffers()
        val bands = b.bands
        for (i in bands.indices) {
            val x = i.toFloat() / config.bandCount
            val wave = 0.5f + 0.5f * sin((phase * 2f * PI + x * 7f).toFloat())
            bands[i] = (0.18f + 0.22f * wave * (1f - x * 0.6f)).coerceIn(0f, 1f)
        }
        val spectrum = b.spectrum
        for (k in spectrum.indices) {
            val x = k.toFloat() / config.spectrumSize
            spectrum[k] = spectrumEnvelope[k] * (0.6f + 0.4f * sin((phase * 3f + x * 40f).toFloat()))
        }
        // L y R comparten array, como antes; el factor lento es común a todas las muestras.
        val wave = b.waveformLeft
        val slowWave = sin(phase * 0.7f)
        for (i in wave.indices) {
            val t = i.toFloat() / config.waveformSize
            wave[i] = 0.25f * sin((t * 2f * PI * 2f + phase * 4f).toFloat()) * slowWave
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

    /** `src`·k en `dst` (puede ser el mismo array); si los tamaños no coinciden se asigna uno nuevo. */
    private fun scaled(src: FloatArray, k: Float, dst: FloatArray): FloatArray {
        val out = if (dst.size == src.size) dst else FloatArray(src.size)
        for (i in src.indices) out[i] = src[i] * k
        return out
    }
}
