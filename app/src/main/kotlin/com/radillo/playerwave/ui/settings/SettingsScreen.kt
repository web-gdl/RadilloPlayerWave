package com.radillo.playerwave.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.radillo.playerwave.BuildConfig
import com.radillo.playerwave.R
import com.radillo.playerwave.core.model.AppSettings
import com.radillo.playerwave.core.model.ThemeMode
import com.radillo.playerwave.data.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: AppSettingsRepository,
) : ViewModel() {

    /** `null` hasta que DataStore entrega el primer valor; así el tema no parpadea al arrancar. */
    val settings: StateFlow<AppSettings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.update { it.copy(themeMode = mode) } }
    }

    fun setArtworkColors(enabled: Boolean) {
        viewModelScope.launch { repository.update { it.copy(artworkColors = enabled) } }
    }
}

/** Bibliotecas de terceros incluidas en la aplicación (ver docs/DEPENDENCIES.md). */
private val ThirdPartyLicenses = listOf(
    "Kotlin y kotlinx.coroutines" to "Apache 2.0",
    "AndroidX (Compose, Material 3, Room, Navigation, Lifecycle, DataStore, Palette)" to "Apache 2.0",
    "AndroidX Media3 (ExoPlayer, MediaSession)" to "Apache 2.0",
    "Dagger Hilt" to "Apache 2.0",
    "Coil" to "Apache 2.0",
    "Material Symbols / Material Icons" to "Apache 2.0",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenEqualizer: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showLicenses by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val current = settings ?: return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SectionLabel(stringResource(R.string.settings_appearance))
            Text(
                stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            ThemeMode.entries.forEach { mode ->
                ListItem(
                    headlineContent = { Text(stringResource(mode.labelRes())) },
                    leadingContent = { RadioButton(selected = current.themeMode == mode, onClick = null) },
                    modifier = Modifier.selectable(
                        selected = current.themeMode == mode,
                        role = Role.RadioButton,
                        onClick = { viewModel.setThemeMode(mode) },
                    ),
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_artwork_colors)) },
                supportingContent = { Text(stringResource(R.string.settings_artwork_colors_hint)) },
                trailingContent = {
                    Switch(checked = current.artworkColors, onCheckedChange = viewModel::setArtworkColors)
                },
            )
            HorizontalDivider()

            SectionLabel(stringResource(R.string.settings_audio))
            ListItem(
                headlineContent = { Text(stringResource(R.string.equalizer_title)) },
                leadingContent = { Icon(Icons.Rounded.GraphicEq, contentDescription = null) },
                trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenEqualizer),
            )
            HorizontalDivider()

            SectionLabel(stringResource(R.string.settings_about))
            ListItem(
                headlineContent = { Text(stringResource(R.string.app_name)) },
                supportingContent = { Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_privacy)) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_licenses)) },
                trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable { showLicenses = true },
            )
        }
    }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text(stringResource(R.string.settings_licenses)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.settings_licenses_intro), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    ThirdPartyLicenses.forEach { (name, license) ->
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            license,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenses = false }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}
