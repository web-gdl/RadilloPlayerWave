package com.radillo.playerwave.visualizer.analysis

/**
 * Instantánea del análisis de audio que consumen las escenas. Inmutable y sin tipos
 * de Android: el motor de análisis la produce ~60 veces por segundo.
 *
 * Todos los valores están normalizados a [0, 1] salvo que se indique lo contrario.
 *
 * @property spectrum Magnitud por bin de FFT (0 Hz .. Nyquist), ya suavizada.
 * @property bands Energía por banda (escala logarítmica), número configurable.
 * @property bass Energía agregada ~20-250 Hz.
 * @property mid Energía agregada ~250-4000 Hz.
 * @property treble Energía agregada ~4-20 kHz.
 * @property rms Nivel RMS del bloque.
 * @property peak Pico absoluto del bloque.
 * @property onset Fuerza de transitorio (0 = nada, 1 = golpe claro) con decaimiento rápido.
 * @property waveformLeft Forma de onda del canal izquierdo en [-1, 1].
 * @property waveformRight Forma de onda del canal derecho en [-1, 1] (igual a L si es mono).
 * @property timestampUs Marca temporal de reproducción a la que corresponde el bloque.
 * @property synthetic `true` cuando no hay audio real y el frame se ha generado de respaldo.
 */
class AudioFrame(
    val spectrum: FloatArray,
    val bands: FloatArray,
    val bass: Float,
    val mid: Float,
    val treble: Float,
    val rms: Float,
    val peak: Float,
    val onset: Float,
    val waveformLeft: FloatArray,
    val waveformRight: FloatArray,
    val timestampUs: Long,
    val synthetic: Boolean,
) {
    companion object {
        /** Frame silencioso con los tamaños indicados; útil para arranque y respaldo. */
        fun silent(spectrumSize: Int, bandCount: Int, waveformSize: Int, synthetic: Boolean = true): AudioFrame =
            AudioFrame(
                spectrum = FloatArray(spectrumSize),
                bands = FloatArray(bandCount),
                bass = 0f,
                mid = 0f,
                treble = 0f,
                rms = 0f,
                peak = 0f,
                onset = 0f,
                waveformLeft = FloatArray(waveformSize),
                waveformRight = FloatArray(waveformSize),
                timestampUs = 0L,
                synthetic = synthetic,
            )
    }
}
