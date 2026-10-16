package com.radillo.playerwave.ui.visualizer

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radillo.playerwave.R
import com.radillo.playerwave.core.model.VisualizerSettings
import com.radillo.playerwave.visualizer.engine.DataState
import com.radillo.playerwave.visualizer.render.VisualizerSurface
import com.radillo.playerwave.visualizer.scenes.AmbientScene
import com.radillo.playerwave.visualizer.scenes.LineFieldScene
import com.radillo.playerwave.visualizer.scenes.OscilloscopeScene
import com.radillo.playerwave.visualizer.scenes.ParticlesScene
import com.radillo.playerwave.visualizer.scenes.RingsScene
import com.radillo.playerwave.visualizer.scenes.SpectrumBarsScene
import com.radillo.playerwave.visualizer.scenes.TunnelScene
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

@StringRes
fun sceneLabelRes(id: String): Int = when (id) {
    SpectrumBarsScene.ID -> R.string.scene_spectrum
    OscilloscopeScene.ID -> R.string.scene_oscilloscope
    RingsScene.ID -> R.string.scene_rings
    ParticlesScene.ID -> R.string.scene_particles
    TunnelScene.ID -> R.string.scene_tunnel
    LineFieldScene.ID -> R.string.scene_lines
    AmbientScene.ID -> R.string.scene_ambient
    else -> R.string.scene_unknown
}

@StringRes
fun paletteLabelRes(id: String): Int = when (id) {
    "aurora" -> R.string.palette_aurora
    "ember" -> R.string.palette_ember
    "ocean" -> R.string.palette_ocean
    "neon" -> R.string.palette_neon
    "mono" -> R.string.palette_mono
    else -> R.string.scene_unknown
}

/**
 * Pantalla del visualizador. En modo normal convive con la barra inferior de la app; en modo
 * inmersivo ocupa toda la pantalla, oculta las barras del sistema y los controles se esconden
 * solos: un toque los muestra u oculta.
 */
@Composable
fun VisualizerScreen(
    immersive: Boolean,
    onEnterImmersive: () -> Unit,
    onExitImmersive: () -> Unit,
    viewModel: VisualizerViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val playback by viewModel.playbackState.collectAsStateWithLifecycle()
    val dataState by viewModel.dataState.collectAsStateWithLifecycle()
    val quality by viewModel.quality.collectAsStateWithLifecycle()
    var controlsVisible by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }

    ImmersiveEffect(enabled = immersive, keepScreenOn = settings.keepScreenOn)

    // En modo inmersivo los controles se ocultan solos tras unos segundos.
    LaunchedEffect(immersive, controlsVisible, showSettings) {
        if (immersive && controlsVisible && !showSettings) {
            delay(4_000)
            controlsVisible = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        VisualizerSurface(
            engine = viewModel.engine,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { controlsVisible = !controlsVisible })
                },
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                TopControls(
                    sceneIds = viewModel.sceneIds,
                    selectedScene = settings.sceneId,
                    immersive = immersive,
                    onSelectScene = viewModel::selectScene,
                    onOpenSettings = { showSettings = true },
                    onToggleImmersive = { if (immersive) onExitImmersive() else onEnterImmersive() },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                BottomControls(
                    title = playback.currentTrack?.title,
                    subtitle = playback.currentTrack?.artist,
                    isPlaying = playback.isPlaying,
                    dataState = dataState,
                    immersive = immersive,
                    onPrevious = viewModel::previous,
                    onTogglePlay = viewModel::togglePlayPause,
                    onNext = viewModel::next,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }

    if (showSettings) {
        VisualizerSettingsSheet(
            settings = settings,
            quality = quality,
            defaultMaxFps = viewModel.profile.defaultMaxFps,
            palettes = viewModel.palettes.map { it.id },
            onPreview = viewModel::preview,
            onCommit = viewModel::commit,
            onSelectPalette = viewModel::selectPalette,
            onKeepScreenOn = viewModel::setKeepScreenOn,
            onAutoQuality = viewModel::setAutoQuality,
            onMaxFps = viewModel::setMaxFps,
            onReset = viewModel::resetTuning,
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun TopControls(
    sceneIds: List<String>,
    selectedScene: String,
    immersive: Boolean,
    onSelectScene: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onToggleImmersive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
            .then(if (immersive) Modifier.statusBarsPadding() else Modifier)
            .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            items(sceneIds, key = { it }) { id ->
                FilterChip(
                    selected = id == selectedScene,
                    onClick = { onSelectScene(id) },
                    label = { Text(stringResource(sceneLabelRes(id))) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Black.copy(alpha = 0.35f),
                        labelColor = Color.White.copy(alpha = 0.85f),
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
        OverlayIconButton(onClick = onOpenSettings) {
            Icon(Icons.Rounded.Tune, contentDescription = stringResource(R.string.vis_settings))
        }
        OverlayIconButton(onClick = onToggleImmersive) {
            Icon(
                imageVector = if (immersive) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                contentDescription = stringResource(if (immersive) R.string.vis_exit_fullscreen else R.string.vis_fullscreen),
            )
        }
    }
}

@Composable
private fun BottomControls(
    title: String?,
    subtitle: String?,
    isPlaying: Boolean,
    dataState: DataState,
    immersive: Boolean,
    onPrevious: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
            .then(if (immersive) Modifier.navigationBarsPadding() else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val statusRes = when {
            title == null -> R.string.vis_idle
            dataState == DataState.SYNTHETIC && isPlaying -> R.string.vis_no_audio_data
            else -> null
        }
        if (statusRes != null) {
            Text(
                text = stringResource(statusRes),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(6.dp))
        }
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OverlayIconButton(onClick = onPrevious) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = stringResource(R.string.action_previous))
                }
                Spacer(Modifier.width(8.dp))
                OverlayIconButton(onClick = onTogglePlay) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.action_pause else R.string.action_play),
                    )
                }
                Spacer(Modifier.width(8.dp))
                OverlayIconButton(onClick = onNext) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.action_next))
                }
            }
        }
    }
}

@Composable
private fun OverlayIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.35f),
            contentColor = Color.White,
        ),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisualizerSettingsSheet(
    settings: VisualizerSettings,
    quality: Float,
    defaultMaxFps: Int,
    palettes: List<String>,
    onPreview: (VisualizerSettings) -> Unit,
    onCommit: ((VisualizerSettings) -> VisualizerSettings) -> Unit,
    onSelectPalette: (String) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onAutoQuality: (Boolean) -> Unit,
    onMaxFps: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Borrador local: se previsualiza al arrastrar y se persiste al soltar.
    var draft by remember(settings) { mutableStateOf(settings) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.vis_settings), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onReset) { Text(stringResource(R.string.vis_reset)) }
            }
            Spacer(Modifier.height(8.dp))

            TuningSlider(
                label = stringResource(R.string.vis_sensitivity),
                value = draft.sensitivity,
                range = VisualizerSettings.SENSITIVITY_RANGE,
                onChange = { v -> draft = draft.copy(sensitivity = v); onPreview(draft) },
                onFinished = { val v = draft.sensitivity; onCommit { it.copy(sensitivity = v) } },
            )
            TuningSlider(
                label = stringResource(R.string.vis_intensity),
                value = draft.intensity,
                range = VisualizerSettings.INTENSITY_RANGE,
                onChange = { v -> draft = draft.copy(intensity = v); onPreview(draft) },
                onFinished = { val v = draft.intensity; onCommit { it.copy(intensity = v) } },
            )
            TuningSlider(
                label = stringResource(R.string.vis_speed),
                value = draft.speed,
                range = VisualizerSettings.SPEED_RANGE,
                onChange = { v -> draft = draft.copy(speed = v); onPreview(draft) },
                onFinished = { val v = draft.speed; onCommit { it.copy(speed = v) } },
            )
            TuningSlider(
                label = stringResource(R.string.vis_density),
                value = draft.density,
                range = VisualizerSettings.DENSITY_RANGE,
                onChange = { v -> draft = draft.copy(density = v); onPreview(draft) },
                onFinished = { val v = draft.density; onCommit { it.copy(density = v) } },
            )

            val syncRange = VisualizerSettings.SYNC_RANGE_MS
            TuningSlider(
                label = stringResource(R.string.vis_sync),
                value = draft.syncOffsetMs.toFloat(),
                range = syncRange.first.toFloat()..syncRange.last.toFloat(),
                valueText = stringResource(R.string.vis_sync_value, draft.syncOffsetMs),
                onChange = { v -> draft = draft.copy(syncOffsetMs = v.roundToInt()); onPreview(draft) },
                onFinished = { val v = draft.syncOffsetMs; onCommit { it.copy(syncOffsetMs = v) } },
            )

            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.vis_palette), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(palettes, key = { it }) { id ->
                    FilterChip(
                        selected = id == settings.paletteId,
                        onClick = { onSelectPalette(id) },
                        label = { Text(stringResource(paletteLabelRes(id))) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.vis_max_fps), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VisualizerSettings.MAX_FPS_OPTIONS.forEach { option ->
                    FilterChip(
                        selected = settings.maxFps == option,
                        onClick = { onMaxFps(option) },
                        label = {
                            Text(
                                if (option == 0) stringResource(R.string.vis_fps_auto, defaultMaxFps)
                                else stringResource(R.string.vis_fps_value, option),
                            )
                        },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.vis_keep_screen_on), modifier = Modifier.weight(1f))
                Switch(checked = settings.keepScreenOn, onCheckedChange = onKeepScreenOn)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.vis_auto_quality))
                    Text(
                        text = if (settings.autoQuality && quality < 0.995f) {
                            stringResource(R.string.vis_auto_quality_current, (quality * 100f).roundToInt())
                        } else {
                            stringResource(R.string.vis_auto_quality_hint)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.autoQuality, onCheckedChange = onAutoQuality)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TuningSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
    valueText: String = String.format(Locale.ROOT, "%.2f×", value),
) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            onValueChangeFinished = onFinished,
            valueRange = range,
        )
    }
}
