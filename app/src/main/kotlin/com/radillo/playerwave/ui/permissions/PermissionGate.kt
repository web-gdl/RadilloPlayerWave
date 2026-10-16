package com.radillo.playerwave.ui.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.radillo.playerwave.R
import com.radillo.playerwave.data.permissions.AudioPermission

/**
 * Pide el permiso de lectura de audio y, una vez concedido, muestra `content`.
 * Si el usuario lo deniega dos veces, Android deja de mostrar el diálogo: se ofrece abrir ajustes.
 */
@Composable
fun AudioPermissionGate(
    onGranted: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(AudioPermission.isGranted(context)) }
    var denials by remember { mutableIntStateOf(0) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) denials++
    }

    // Vuelve a comprobar al volver de ajustes del sistema.
    LifecycleResumeEffect(Unit) {
        granted = AudioPermission.isGranted(context)
        onPauseOrDispose { }
    }

    LaunchedEffect(granted) { if (granted) onGranted() }

    if (granted) {
        content()
    } else {
        PermissionExplanation(
            showSettings = denials >= 2,
            onRequest = { launcher.launch(AudioPermission.permission) },
            onOpenSettings = { openAppSettings(context) },
        )
    }
}

/** Pide `POST_NOTIFICATIONS` (API 33+) una sola vez; no bloquea nada si se deniega. */
@Composable
fun RequestNotificationPermissionOnce() {
    val permission = AudioPermission.notificationPermissionOrNull() ?: return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { launcher.launch(permission) }
}

@Composable
private fun PermissionExplanation(
    showSettings: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.LibraryMusic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.permission_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.permission_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        if (showSettings) {
            Button(onClick = onOpenSettings) { Text(stringResource(R.string.permission_open_settings)) }
            TextButton(onClick = onRequest) { Text(stringResource(R.string.permission_retry)) }
        } else {
            Button(onClick = onRequest) { Text(stringResource(R.string.permission_grant)) }
        }
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
