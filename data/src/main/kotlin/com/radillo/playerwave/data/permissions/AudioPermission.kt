package com.radillo.playerwave.data.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Permiso de lectura de audio según la versión de Android:
 * - API 33+: `READ_MEDIA_AUDIO` (permisos granulares de medios).
 * - API 24-32: `READ_EXTERNAL_STORAGE`.
 *
 * Las notificaciones (`POST_NOTIFICATIONS`, API 33+) se piden aparte y no bloquean la app.
 */
object AudioPermission {

    fun forSdk(sdkInt: Int): String =
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    val permission: String get() = forSdk(Build.VERSION.SDK_INT)

    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** `POST_NOTIFICATIONS` solo existe desde API 33; en versiones anteriores no hace falta pedir nada. */
    fun notificationPermissionOrNull(sdkInt: Int = Build.VERSION.SDK_INT): String? =
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
}
