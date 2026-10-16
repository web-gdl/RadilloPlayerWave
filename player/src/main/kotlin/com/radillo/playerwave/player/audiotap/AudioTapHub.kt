package com.radillo.playerwave.player.audiotap

/**
 * Punto de encuentro entre el servicio (que crea el `ExoPlayer` y el procesador) y el resto
 * de la app (que consume el `AudioSource`). Único por proceso: solo existe un reproductor.
 */
object AudioTapHub {
    val source: TapAudioSource = TapAudioSource()

    /** Procesador nuevo ligado a [source]; se crea uno por cada `ExoPlayer`. */
    fun newProcessor(): PcmTapAudioProcessor = PcmTapAudioProcessor(source)
}
