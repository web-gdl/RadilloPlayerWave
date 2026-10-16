package com.radillo.playerwave.data.settings

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Flujo que emite `read(prefs)` al suscribirse y cada vez que cambia alguna clave del archivo.
 * `SharedPreferences` se lee en memoria (ya cargado) y no necesita E/S en el hilo principal tras
 * la primera carga; las escrituras usan `apply()` (asíncronas).
 */
internal fun <T> SharedPreferences.observe(read: (SharedPreferences) -> T): Flow<T> = callbackFlow {
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, _ -> trySend(read(prefs)) }
    // La referencia fuerte al listener vive mientras dura el flujo (SharedPreferences la guarda débil).
    registerOnSharedPreferenceChangeListener(listener)
    trySend(read(this@observe))
    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
}.conflate().distinctUntilChanged()
