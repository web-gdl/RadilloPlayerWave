package com.radillo.playerwave.ui.library

import android.content.Context

/**
 * Carpeta, álbum o artista abierto al cerrar la app. Al volver a la biblioteca se reabre: en el
 * coche se suele escuchar «la carpeta de la USB» y no hay que navegar hasta ella cada vez.
 */
object LastCollection {
    private const val KEY_KIND = "last_collection_kind"
    private const val KEY_KEY = "last_collection_key"
    private const val KEY_TITLE = "last_collection_title"

    data class Saved(val kind: CollectionKind, val key: String, val title: String)

    private fun prefs(context: Context) = context.getSharedPreferences(LibraryViewModel.UI_PREFS, Context.MODE_PRIVATE)

    fun save(context: Context, kind: CollectionKind, key: String, title: String) {
        prefs(context).edit().putString(KEY_KIND, kind.name).putString(KEY_KEY, key).putString(KEY_TITLE, title).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_KIND).remove(KEY_KEY).remove(KEY_TITLE).apply()
    }

    fun read(context: Context): Saved? {
        val p = prefs(context)
        val kind = CollectionKind.entries.firstOrNull { it.name == p.getString(KEY_KIND, null) } ?: return null
        val key = p.getString(KEY_KEY, null) ?: return null
        return Saved(kind, key, p.getString(KEY_TITLE, null).orEmpty())
    }
}
