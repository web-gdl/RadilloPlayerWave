package com.radillo.playerwave.data.library

import com.radillo.playerwave.core.model.Album
import com.radillo.playerwave.core.model.Artist
import com.radillo.playerwave.core.model.Folder
import com.radillo.playerwave.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repositorio de biblioteca: MediaStore como fuente de verdad, Room como caché observable.
 */
interface LibraryRepository {
    val tracks: Flow<List<Track>>
    val albums: Flow<List<Album>>
    val artists: Flow<List<Artist>>
    val folders: Flow<List<Folder>>
    /** `true` solo mientras se lee MediaStore completo (no durante la comprobación rápida). */
    val isScanning: StateFlow<Boolean>

    fun tracksOfAlbum(albumId: Long): Flow<List<Track>>
    fun tracksOfArtist(artistId: Long): Flow<List<Track>>
    fun tracksOfFolder(folderPath: String): Flow<List<Track>>
    fun search(query: String): Flow<List<Track>>

    /** Una canción de la caché por su id de MediaStore, o null. */
    suspend fun track(id: Long): Track?

    /**
     * Sincroniza la caché local con MediaStore. Sin [force], primero compara una huella barata y
     * no escanea si nada cambió (arranque instantáneo con la biblioteca ya guardada). Con [force]
     * (botón «Buscar música nueva») siempre lee MediaStore completo. Seguro de llamar repetidamente.
     */
    suspend fun rescan(force: Boolean = false): ScanResult
}
