package com.radillo.playerwave.data.library

import com.radillo.playerwave.core.model.Album
import com.radillo.playerwave.core.model.Artist
import com.radillo.playerwave.core.model.Folder
import com.radillo.playerwave.core.model.Track
import kotlinx.coroutines.flow.Flow

/**
 * Contrato del repositorio de biblioteca. La implementación (MediaStore + Room)
 * llega en la Fase 1; aquí se fija la API que consumirán las pantallas.
 */
interface LibraryRepository {
    val tracks: Flow<List<Track>>
    val albums: Flow<List<Album>>
    val artists: Flow<List<Artist>>
    val folders: Flow<List<Folder>>

    fun tracksOfAlbum(albumId: Long): Flow<List<Track>>
    fun tracksOfArtist(artistId: Long): Flow<List<Track>>
    fun tracksOfFolder(folderPath: String): Flow<List<Track>>
    fun search(query: String): Flow<List<Track>>

    /** Vuelve a leer MediaStore y sincroniza la caché local. */
    suspend fun rescan()
}
