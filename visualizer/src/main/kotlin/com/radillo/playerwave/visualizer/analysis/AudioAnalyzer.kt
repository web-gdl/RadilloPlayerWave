package com.radillo.playerwave.visualizer.analysis

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/** Parámetros del analizador. Los tamaños determinan las longitudes de los arrays de [AudioFrame]. */
class AnalyzerConfig(
    val fftSize: Int = 2048,
    val bandCount: Int = 64,
    val waveformSize: Int = 512,
    val minHz: Float = 30f,
    val maxHz: Float = 16_000f,
    val framesPerSecond: Int = 60,
) {
    val spectrumSize: Int get() = fftSize / 2
}

/**
 * Convierte bloques de PCM en [AudioFrame]s. Kotlin puro, sin Android: se prueba con JUnit.
 *
 * Pipeline por frame (cada `sampleRate / framesPerSecond` muestras):
 * ventana Hann → FFT → magnitudes → bandas logarítmicas → escala dB (−60..0 dB → 0..1)
 * → AGC lento → detector de transitorios → suavizado ataque/liberación → bass/mid/treble.
 *
 * Asigna los arrays del frame emitido (son inmutables para el consumidor); el resto de buffers
 * se reutiliza. Las llamadas deben hacerse desde un único hilo.
 */
class AudioAnalyzer(val config: AnalyzerConfig = AnalyzerConfig()) {

    private val n = config.fftSize
    private val fft = Fft(n)
    private val window = Fft.hannWindow(n)
    private val re = FloatArray(n)
    private val im = FloatArray(n)
    private val magnitudes = FloatArray(config.spectrumSize)
    private val spectrumLevels = FloatArray(config.spectrumSize)

    // Ventana deslizante mono y formas de onda L/R (buffers circulares).
    private val mono = FloatArray(n)
    private val left = FloatArray(config.waveformSize)
    private val right = FloatArray(config.waveformSize)
    private var monoIndex = 0
    private var waveIndex = 0

    private val bandRaw = FloatArray(config.bandCount)
    private val bandSmoother = AttackReleaseSmoother(config.bandCount, attackTau = 0.025f, releaseTau = 0.11f)
    private val spectrumSmoother = AttackReleaseSmoother(config.spectrumSize, attackTau = 0.02f, releaseTau = 0.09f)
    private val onsetDetector = OnsetDetector(config.bandCount)
    private val agc = Agc()
    private val rmsSmoother = ScalarSmoother(attackTau = 0.02f, releaseTau = 0.15f)

    private var sampleRate = 0
    private var hop = 1
    private var dtSeconds = 1f / 60f
    private var mapper: BandMapper? = null
    private var bassEnd = 0
    private var midEnd = 0

    private var samplesSinceFrame = 0
    private var sumSquares = 0.0
    private var peakAbs = 0f

    /** Frames emitidos desde la creación (para pruebas y diagnóstico). */
    var emittedFrames = 0L
        private set

    /** Procesa un bloque; invoca `onFrame` por cada [AudioFrame] completado. */
    fun push(block: PcmBlock, onFrame: (AudioFrame) -> Unit) {
        if (block.sampleRateHz != sampleRate) reconfigure(block.sampleRateHz)
        val samples = block.samples
        val channels = block.channelCount.coerceAtLeast(1)
        val frames = samples.size / channels
        val waveSize = config.waveformSize
        for (f in 0 until frames) {
            val base = f * channels
            val l = samples[base]
            val r = if (channels > 1) samples[base + 1] else l
            val m = (l + r) * 0.5f

            mono[monoIndex] = m
            if (++monoIndex == n) monoIndex = 0
            left[waveIndex] = l
            right[waveIndex] = r
            if (++waveIndex == waveSize) waveIndex = 0

            sumSquares += (m * m).toDouble()
            val a = abs(m)
            if (a > peakAbs) peakAbs = a
            samplesSinceFrame++

            if (samplesSinceFrame >= hop) {
                val timestampUs = block.timestampUs + (f + 1).toLong() * 1_000_000L / sampleRate
                onFrame(emitFrame(timestampUs))
            }
        }
    }

    fun reset() {
        mono.fill(0f); left.fill(0f); right.fill(0f)
        monoIndex = 0; waveIndex = 0
        bandSmoother.reset(); spectrumSmoother.reset(); onsetDetector.reset(); agc.reset(); rmsSmoother.reset()
        samplesSinceFrame = 0; sumSquares = 0.0; peakAbs = 0f
    }

    private fun reconfigure(rate: Int) {
        sampleRate = rate.coerceAtLeast(8_000)
        hop = max(64, sampleRate / config.framesPerSecond)
        dtSeconds = hop.toFloat() / sampleRate
        val m = BandMapper(sampleRate, n, config.bandCount, config.minHz, config.maxHz)
        mapper = m
        bassEnd = m.bandIndexFor(250f).coerceIn(1, config.bandCount - 2)
        midEnd = m.bandIndexFor(4_000f).coerceIn(bassEnd + 1, config.bandCount - 1)
        reset()
    }

    private fun emitFrame(timestampUs: Long): AudioFrame {
        val mapper = checkNotNull(mapper)
        // Desenrolla la ventana circular (la muestra más antigua primero) y aplica Hann.
        var idx = monoIndex
        for (i in 0 until n) {
            re[i] = mono[idx] * window[i]
            im[i] = 0f
            if (++idx == n) idx = 0
        }
        fft.transform(re, im)
        // Con ventana Hann, un seno de amplitud A produce un pico ≈ A·N/4 → se normaliza a A.
        fft.magnitudes(re, im, magnitudes, scale = 4f / n)

        mapper.apply(magnitudes, bandRaw)
        for (i in bandRaw.indices) bandRaw[i] = toLevel(bandRaw[i])
        val gain = agc.update(bandRaw, dtSeconds)
        for (i in bandRaw.indices) bandRaw[i] = (bandRaw[i] * gain).coerceAtMost(1f)

        val onset = onsetDetector.update(bandRaw, dtSeconds)
        bandSmoother.update(bandRaw, dtSeconds)

        for (k in spectrumLevels.indices) spectrumLevels[k] = (toLevel(magnitudes[k]) * gain).coerceAtMost(1f)
        spectrumSmoother.update(spectrumLevels, dtSeconds)

        val bands = bandSmoother.values
        val bass = mean(bands, 0, bassEnd)
        val mid = mean(bands, bassEnd, midEnd)
        val treble = mean(bands, midEnd, bands.size)

        val count = samplesSinceFrame.coerceAtLeast(1)
        val rms = rmsSmoother.update((sqrt(sumSquares / count).toFloat() * 2.2f).coerceAtMost(1f), dtSeconds)
        val peak = peakAbs.coerceAtMost(1f)
        sumSquares = 0.0
        peakAbs = 0f
        samplesSinceFrame = 0

        emittedFrames++
        return AudioFrame(
            spectrum = spectrumSmoother.values.copyOf(),
            bands = bands.copyOf(),
            bass = bass,
            mid = mid,
            treble = treble,
            rms = rms,
            peak = peak,
            onset = onset,
            waveformLeft = unroll(left, waveIndex),
            waveformRight = unroll(right, waveIndex),
            timestampUs = timestampUs,
            synthetic = false,
        )
    }

    private fun unroll(circular: FloatArray, nextIndex: Int): FloatArray {
        val out = FloatArray(circular.size)
        var idx = nextIndex
        for (i in out.indices) {
            out[i] = circular[idx]
            if (++idx == circular.size) idx = 0
        }
        return out
    }

    private fun mean(values: FloatArray, from: Int, to: Int): Float {
        if (to <= from) return 0f
        var s = 0f
        for (i in from until to) s += values[i]
        return s / (to - from)
    }

    companion object {
        /** Magnitud lineal (0..1) → nivel perceptual: −60 dB → 0, 0 dB → 1. */
        fun toLevel(magnitude: Float): Float {
            val db = 20f * log10(max(magnitude, 1e-4f))
            return ((db + 60f) / 60f).coerceIn(0f, 1f)
        }
    }
}
