package com.radillo.playerwave.data.library

import com.google.common.truth.Truth.assertThat
import com.radillo.playerwave.data.library.db.TrackEntity
import org.junit.Test

class LibrarySyncTest {

    private fun entity(id: Long, title: String = "t$id") = TrackEntity(
        id = id, uri = "content://x/$id", title = title, artist = "a", artistId = 1, album = "b", albumId = 2,
        durationMs = 1000, trackNumber = 1, year = 2000, folderPath = "/usb", fileName = "$id.mp3",
        mimeType = "audio/mpeg", dateAddedSec = 1, sizeBytes = 10,
    )

    @Test
    fun `sin cambios no se escribe nada`() {
        val same = listOf(entity(1), entity(2))
        val diff = LibraryDiff.compute(same, same.map { it.copy() })
        assertThat(diff.upserts).isEmpty()
        assertThat(diff.deletes).isEmpty()
    }

    @Test
    fun `detecta nuevas, modificadas y borradas`() {
        val existing = listOf(entity(1), entity(2), entity(3))
        val scanned = listOf(entity(1), entity(2, title = "renombrada"), entity(4))
        val diff = LibraryDiff.compute(existing, scanned)
        assertThat(diff.added).isEqualTo(1)
        assertThat(diff.updated).isEqualTo(1)
        assertThat(diff.upserts.map { it.id }).containsExactly(2L, 4L)
        assertThat(diff.deletes).containsExactly(3L)
    }

    @Test
    fun `la huella se guarda y se recupera`() {
        val sig = LibrarySignature(count = 1248, maxId = 99_871, maxModifiedSec = 1_760_000_000, totalBytes = 7_301_234_567)
        assertThat(LibrarySignature.decode(sig.encode())).isEqualTo(sig)
        assertThat(LibrarySignature.decode(null)).isNull()
        assertThat(LibrarySignature.decode("basura")).isNull()
    }

    @Test
    fun `el resultado indica si hubo cambios`() {
        assertThat(ScanResult.unchanged(10).changed).isFalse()
        assertThat(ScanResult.unchanged(10).skipped).isTrue()
        assertThat(ScanResult(added = 2, removed = 0, updated = 0, total = 12).changed).isTrue()
    }
}
