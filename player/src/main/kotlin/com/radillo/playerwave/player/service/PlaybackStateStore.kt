package com.radillo.playerwave.player.service

import com.radillo.playerwave.core.model.Track
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Lo que había sonando al cerrar la app: cola, canción, posición, aleatorio y repetición.
 * Se guarda en un JSON pequeño en `filesDir` con escritura atómica y sincronizada (`AtomicFile`).
 */
internal data class SavedPlayback(
    val tracks: List<Track>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
)

internal class PlaybackStateStore(private val file: File) {

    /** `AtomicFile` hace `fsync` antes de sustituir: un corte de corriente (la llave) no deja el archivo vacío. */
    private val atomic = AtomicFile(file)

    fun read(): SavedPlayback? = try {
        decode(String(atomic.readFully(), Charsets.UTF_8)) // sin archivo: FileNotFoundException → null
    } catch (_: Exception) {
        null // archivo dañado: se empieza de cero
    }

    @Synchronized
    fun write(saved: SavedPlayback) {
        var out: java.io.FileOutputStream? = null
        try {
            out = atomic.startWrite()
            out.write(encode(saved).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(out)
        } catch (_: Exception) {
            out?.let { atomic.failWrite(it) }
        }
    }

    companion object {
        /** Límite de la cola guardada: una biblioteca entera de coche cabe de sobra. */
        const val MAX_TRACKS = 5_000

        /** Recorta la cola alrededor de la canción actual si supera [MAX_TRACKS]. */
        fun trimmed(saved: SavedPlayback): SavedPlayback {
            if (saved.tracks.size <= MAX_TRACKS) return saved
            val start = (saved.index - MAX_TRACKS / 2).coerceIn(0, saved.tracks.size - MAX_TRACKS)
            return saved.copy(tracks = saved.tracks.subList(start, start + MAX_TRACKS), index = saved.index - start)
        }

        fun encode(saved: SavedPlayback): String {
            val s = trimmed(saved)
            val array = JSONArray()
            s.tracks.forEach { t ->
                array.put(
                    JSONObject()
                        .put("id", t.id).put("uri", t.uri).put("title", t.title)
                        .put("artist", t.artist).put("artistId", t.artistId)
                        .put("album", t.album).put("albumId", t.albumId)
                        .put("duration", t.durationMs).put("track", t.trackNumber).put("year", t.year)
                        .put("folder", t.folderPath).put("file", t.fileName).put("mime", t.mimeType)
                        .put("added", t.dateAddedSec).put("size", t.sizeBytes),
                )
            }
            return JSONObject()
                .put("version", 1)
                .put("index", s.index)
                .put("position", s.positionMs)
                .put("shuffle", s.shuffle)
                .put("repeat", s.repeatMode)
                .put("tracks", array)
                .toString()
        }

        fun decode(json: String): SavedPlayback? {
            val root = JSONObject(json)
            val array = root.optJSONArray("tracks") ?: JSONArray()
            val tracks = ArrayList<Track>(array.length())
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                tracks += Track(
                    id = o.getLong("id"),
                    uri = o.getString("uri"),
                    title = o.optString("title"),
                    artist = o.optString("artist"),
                    artistId = o.optLong("artistId"),
                    album = o.optString("album"),
                    albumId = o.optLong("albumId"),
                    durationMs = o.optLong("duration"),
                    trackNumber = o.optInt("track"),
                    year = o.optInt("year"),
                    folderPath = o.optString("folder"),
                    fileName = o.optString("file"),
                    mimeType = o.optString("mime"),
                    dateAddedSec = o.optLong("added"),
                    sizeBytes = o.optLong("size"),
                )
            }
            return SavedPlayback(
                tracks = tracks,
                index = root.optInt("index").coerceIn(0, (tracks.size - 1).coerceAtLeast(0)),
                positionMs = root.optLong("position").coerceAtLeast(0L),
                shuffle = root.optBoolean("shuffle"),
                repeatMode = root.optInt("repeat"),
            )
        }
    }
}
