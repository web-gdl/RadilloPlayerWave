package com.radillo.playerwave.data.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Pruebas de las funciones puras del escáner (sin Android). */
class MediaStoreScannerTest {

    @Test
    fun `parentOf devuelve la carpeta del archivo`() {
        assertThat(FolderPath.parentOf("/storage/emulated/0/Music/Album/01.mp3"))
            .isEqualTo("/storage/emulated/0/Music/Album")
        assertThat(FolderPath.parentOf("archivo.mp3")).isEmpty()
        assertThat(FolderPath.parentOf("")).isEmpty()
        assertThat(FolderPath.parentOf("/solo.mp3")).isEmpty()
    }

    @Test
    fun `displayName es el ultimo segmento`() {
        assertThat(FolderPath.displayName("/storage/emulated/0/Music")).isEqualTo("Music")
        assertThat(FolderPath.displayName("Music")).isEqualTo("Music")
    }

    @Test
    fun `normalizeUnknown limpia el marcador de MediaStore`() {
        assertThat(MediaStoreScanner.normalizeUnknown("<unknown>")).isEmpty()
        assertThat(MediaStoreScanner.normalizeUnknown(null)).isEmpty()
        assertThat(MediaStoreScanner.normalizeUnknown("  ")).isEmpty()
        assertThat(MediaStoreScanner.normalizeUnknown("Artista")).isEqualTo("Artista")
    }

    @Test
    fun `normalizeTrackNumber elimina el numero de disco`() {
        assertThat(MediaStoreScanner.normalizeTrackNumber(2003)).isEqualTo(3)
        assertThat(MediaStoreScanner.normalizeTrackNumber(7)).isEqualTo(7)
        assertThat(MediaStoreScanner.normalizeTrackNumber(0)).isEqualTo(0)
        assertThat(MediaStoreScanner.normalizeTrackNumber(-1)).isEqualTo(0)
    }
}
