package com.radillo.playerwave.data.backup

import com.radillo.playerwave.data.library.db.TrackEntity
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * Canción descrita sin depender solo del id de MediaStore, que cambia si el sistema reconstruye su
 * base de medios o se remonta la USB. `id` es el que tenía al guardarse (se prueba después de la
 * ruta), `addedAtMs` solo se usa en favoritos.
 */
data class BackupTrack(
    val id: Long,
    val path: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val addedAtMs: Long = 0L,
) {
    /** Clave para no duplicar: la ruta si la hay; si no, título, artista y duración. */
    val key: String
        get() = path.lowercase().ifBlank { "${title.lowercase()}|${artist.lowercase()}|${durationMs / 1000}" }

    companion object {
        fun of(t: TrackEntity, addedAtMs: Long = 0L) = BackupTrack(
            id = t.id,
            path = pathOf(t),
            title = t.title,
            artist = t.artist,
            durationMs = t.durationMs,
            addedAtMs = addedAtMs,
        )

        fun pathOf(t: TrackEntity): String =
            if (t.folderPath.isBlank()) t.fileName else "${t.folderPath}/${t.fileName}"
    }
}

data class BackupPlaylist(val name: String, val createdAtMs: Long, val tracks: List<BackupTrack>)

/** Favoritos y listas, en orden. */
data class CollectionsSnapshot(
    val favorites: List<BackupTrack> = emptyList(),
    val playlists: List<BackupPlaylist> = emptyList(),
) {
    val isEmpty: Boolean get() = favorites.isEmpty() && playlists.isEmpty()

    val trackCount: Int get() = favorites.size + playlists.sumOf { it.tracks.size }

    /** Une dos respaldos sin duplicar canciones; las listas se juntan por nombre (sin mayúsculas). */
    operator fun plus(other: CollectionsSnapshot): CollectionsSnapshot {
        val favorites = (favorites + other.favorites).distinctBy { it.key }
        val byName = LinkedHashMap<String, BackupPlaylist>()
        (playlists + other.playlists).forEach { p ->
            val k = p.name.trim().lowercase()
            val prev = byName[k]
            byName[k] = if (prev == null) p else prev.copy(tracks = (prev.tracks + p.tracks).distinctBy { it.key })
        }
        return CollectionsSnapshot(favorites, byName.values.toList())
    }

    companion object {
        val EMPTY = CollectionsSnapshot()
    }
}

/**
 * El archivo de respaldo: lo que hay en la app (`saved`) y lo que vino de un respaldo anterior pero
 * aún no aparece en la biblioteca (`pending`, p. ej. canciones de una USB que no está puesta).
 */
data class BackupFile(val saved: CollectionsSnapshot, val pending: CollectionsSnapshot = CollectionsSnapshot.EMPTY) {
    val everything: CollectionsSnapshot get() = saved + pending
    val isEmpty: Boolean get() = saved.isEmpty && pending.isEmpty
}

/** JSON legible (se puede abrir y revisar con cualquier editor de texto). */
object BackupCodec {
    const val FORMAT = 1

    fun encode(file: BackupFile, savedAtMs: Long): String = JSONObject()
        .put("app", "RadilloPlayerWave")
        .put("format", FORMAT)
        .put("savedAtMs", savedAtMs)
        .put("favorites", tracksJson(file.saved.favorites))
        .put("playlists", playlistsJson(file.saved.playlists))
        .put(
            "pending",
            JSONObject()
                .put("favorites", tracksJson(file.pending.favorites))
                .put("playlists", playlistsJson(file.pending.playlists)),
        )
        .toString(2)

    /** `null` si el texto no es un respaldo de la app. */
    fun decode(text: String): BackupFile? = runCatching {
        val root = JSONObject(text)
        if (!root.has("favorites") && !root.has("playlists")) return null
        val pending = root.optJSONObject("pending")
        BackupFile(
            saved = CollectionsSnapshot(tracks(root.optJSONArray("favorites")), playlists(root.optJSONArray("playlists"))),
            pending = CollectionsSnapshot(
                tracks(pending?.optJSONArray("favorites")),
                playlists(pending?.optJSONArray("playlists")),
            ),
        )
    }.getOrNull()

    private fun tracksJson(list: List<BackupTrack>) = JSONArray().apply {
        list.forEach { t ->
            put(
                JSONObject()
                    .put("id", t.id)
                    .put("path", t.path)
                    .put("title", t.title)
                    .put("artist", t.artist)
                    .put("durationMs", t.durationMs)
                    .apply { if (t.addedAtMs > 0) put("addedAtMs", t.addedAtMs) },
            )
        }
    }

    private fun playlistsJson(list: List<BackupPlaylist>) = JSONArray().apply {
        list.forEach { p ->
            put(JSONObject().put("name", p.name).put("createdAtMs", p.createdAtMs).put("tracks", tracksJson(p.tracks)))
        }
    }

    private fun tracks(array: JSONArray?): List<BackupTrack> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            BackupTrack(
                id = o.optLong("id", -1L),
                path = o.optString("path"),
                title = o.optString("title"),
                artist = o.optString("artist"),
                durationMs = o.optLong("durationMs"),
                addedAtMs = o.optLong("addedAtMs"),
            ).takeIf { it.path.isNotBlank() || it.title.isNotBlank() }
        }
    }

    private fun playlists(array: JSONArray?): List<BackupPlaylist> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name").trim()
            if (name.isEmpty()) null else BackupPlaylist(name, o.optLong("createdAtMs"), tracks(o.optJSONArray("tracks")))
        }
    }
}

/**
 * Busca una canción del respaldo en la biblioteca actual: primero por ruta, luego por el id de
 * entonces (si sigue siendo la misma canción) y al final por título, artista y duración (±2 s).
 */
class TrackMatcher(tracks: List<TrackEntity>) {
    private val byPath = tracks.associateBy { BackupTrack.pathOf(it).lowercase() }
    private val byId = tracks.associateBy { it.id }
    private val byTitle = tracks.groupBy { titleKey(it.title, it.artist) }

    fun find(t: BackupTrack): Long? {
        if (t.path.isNotBlank()) byPath[t.path.lowercase()]?.let { return it.id }
        byId[t.id]?.let { if (it.title.equals(t.title, ignoreCase = true)) return it.id }
        val candidates = byTitle[titleKey(t.title, t.artist)].orEmpty()
        return candidates.minByOrNull { abs(it.durationMs - t.durationMs) }
            ?.takeIf { t.durationMs <= 0 || abs(it.durationMs - t.durationMs) <= 2_000 }
            ?.id
    }

    private fun titleKey(title: String, artist: String) = "${title.trim().lowercase()}|${artist.trim().lowercase()}"
}
