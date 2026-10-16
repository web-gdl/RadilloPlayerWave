package com.radillo.playerwave.ui.effects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.radillo.playerwave.R
import com.radillo.playerwave.data.settings.AudioEffectsSettingsRepository
import com.radillo.playerwave.player.audiofx.AudioEffectsController
import com.radillo.playerwave.player.audiofx.AudioEffectsState
import com.radillo.playerwave.player.audiofx.EqBand
import com.radillo.playerwave.ui.library.EmptyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Ecualizador: los cambios se aplican al instante en [AudioEffectsController] y se persisten
 * al soltar el control (no en cada movimiento del deslizador).
 */
@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val controller: AudioEffectsController,
    private val settings: AudioEffectsSettingsRepository,
) : ViewModel() {

    val state: StateFlow<AudioEffectsState> = controller.state

    fun setEnabled(enabled: Boolean) {
        controller.setEnabled(enabled)
        persist()
    }

    fun previewBandLevel(band: Int, levelMb: Int) = controller.setBandLevel(band, levelMb)

    fun usePreset(index: Int) {
        controller.usePreset(index)
        persist()
    }

    fun previewBassBoost(strength: Int) = controller.setBassBoost(strength)

    fun reset() {
        state.value.bands.forEach { controller.setBandLevel(it.index, 0) }
        controller.setBassBoost(0)
        persist()
    }

    fun persist() {
        val current = controller.currentSettings()
        viewModelScope.launch { settings.update { current } }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    viewModel: EqualizerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.equalizer_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.available && state.enabled) {
                        IconButton(onClick = viewModel::reset) {
                            Icon(Icons.Rounded.RestartAlt, contentDescription = stringResource(R.string.eq_reset))
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            !state.checked -> EmptyState(stringResource(R.string.eq_needs_playback))
            !state.available -> EmptyState(stringResource(R.string.eq_unavailable))
            else -> Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.eq_enable)) },
                    trailingContent = {
                        Switch(checked = state.enabled, onCheckedChange = viewModel::setEnabled)
                    },
                )
                HorizontalDivider()

                if (state.presets.isNotEmpty()) {
                    SectionLabel(stringResource(R.string.eq_presets))
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        state.presets.forEachIndexed { index, name ->
                            FilterChip(
                                selected = state.presetIndex == index,
                                onClick = { viewModel.usePreset(index) },
                                label = { Text(name) },
                                enabled = state.enabled,
                            )
                        }
                    }
                }

                SectionLabel(stringResource(R.string.eq_bands))
                state.bands.forEach { band ->
                    BandSlider(
                        band = band,
                        minMb = state.minLevelMb,
                        maxMb = state.maxLevelMb,
                        enabled = state.enabled,
                        onPreview = { viewModel.previewBandLevel(band.index, it) },
                        onCommit = viewModel::persist,
                    )
                }

                if (state.bassBoostSupported) {
                    SectionLabel(stringResource(R.string.eq_bass_boost))
                    BassBoostSlider(
                        strength = state.bassBoostStrength,
                        enabled = state.enabled,
                        onPreview = viewModel::previewBassBoost,
                        onCommit = viewModel::persist,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun BandSlider(
    band: EqBand,
    minMb: Int,
    maxMb: Int,
    enabled: Boolean,
    onPreview: (Int) -> Unit,
    onCommit: () -> Unit,
) {
    var dragValue by remember(band.index) { mutableStateOf<Float?>(null) }
    val shown = dragValue ?: band.levelMb.toFloat()

    Column(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatHz(band.centerHz), style = MaterialTheme.typography.labelLarge)
            Text(
                formatDb(shown.roundToInt()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = shown.coerceIn(minMb.toFloat(), maxMb.toFloat()),
            onValueChange = { v ->
                dragValue = v
                onPreview(v.roundToInt())
            },
            onValueChangeFinished = {
                dragValue = null
                onCommit()
            },
            valueRange = minMb.toFloat()..maxMb.toFloat(),
            enabled = enabled,
        )
    }
}

@Composable
private fun BassBoostSlider(
    strength: Int,
    enabled: Boolean,
    onPreview: (Int) -> Unit,
    onCommit: () -> Unit,
) {
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val shown = dragValue ?: strength.toFloat()
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.eq_bass_boost), style = MaterialTheme.typography.labelLarge)
            Text(
                "${(shown / 10f).roundToInt()} %",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = shown.coerceIn(0f, 1000f),
            onValueChange = { v ->
                dragValue = v
                onPreview(v.roundToInt())
            },
            onValueChangeFinished = {
                dragValue = null
                onCommit()
            },
            valueRange = 0f..1000f,
            enabled = enabled,
        )
        Spacer(Modifier.height(8.dp))
    }
}

private fun formatHz(hz: Int): String =
    if (hz >= 1000) String.format(Locale.getDefault(), "%.1f kHz", hz / 1000f).replace(".0 ", " ").replace(",0 ", " ")
    else "$hz Hz"

private fun formatDb(mb: Int): String = String.format(Locale.getDefault(), "%+.1f dB", mb / 100f)
