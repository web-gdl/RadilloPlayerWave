package com.radillo.playerwave.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extrae un color de acento de una carátula (`content://…/albumart/<id>`) con `Palette`.
 * Decodifica a tamaño reducido en `Dispatchers.IO` y cachea por URI. Devuelve null si no hay
 * carátula o no se puede decodificar.
 */
class ArtworkAccentExtractor(private val context: Context) {

    private val cache = LruCache<String, Int>(64)

    suspend fun accentFor(artworkUri: String): Color? {
        cache.get(artworkUri)?.let { return if (it == NONE) null else Color(it) }
        val argb = withContext(Dispatchers.IO) { extract(artworkUri) }
        cache.put(artworkUri, argb ?: NONE)
        return argb?.let { Color(it) }
    }

    private fun extract(uriString: String): Int? {
        val uri = Uri.parse(uriString)
        val bitmap = decodeSmall(uri) ?: return null
        return try {
            val palette = Palette.from(bitmap).maximumColorCount(16).generate()
            val swatch = palette.vibrantSwatch
                ?: palette.lightVibrantSwatch
                ?: palette.darkVibrantSwatch
                ?: palette.dominantSwatch
            swatch?.rgb
        } catch (e: Exception) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeSmall(uri: Uri): Bitmap? {
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val probe = resolver.openInputStream(uri) ?: return null
            probe.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= TARGET_PX && bounds.outHeight / (sample * 2) >= TARGET_PX) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val TARGET_PX = 96
        const val NONE = 0
    }
}
