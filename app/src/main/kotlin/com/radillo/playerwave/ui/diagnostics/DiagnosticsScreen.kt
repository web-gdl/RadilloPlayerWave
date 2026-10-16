package com.radillo.playerwave.ui.diagnostics

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.radillo.playerwave.BuildConfig
import com.radillo.playerwave.R
import com.radillo.playerwave.core.device.DeviceProfile
import com.radillo.playerwave.core.device.GpuInfo
import com.radillo.playerwave.core.device.GpuProbe
import com.radillo.playerwave.data.settings.VisualizerSettingsRepository
import com.radillo.playerwave.visualizer.engine.VisualizerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

/** Instantánea de memoria del proceso y del sistema. */
data class MemorySnapshot(
    val availableMb: Long,
    val pssMb: Long,
    val heapUsedMb: Long,
    val heapMaxMb: Long,
)

/**
 * Pantalla de diagnóstico: lo que hace falta saber de una unidad desconocida (RAM, nivel de API
 * real, GPU, fps del visualizador) para ajustar la app sin `adb`. El informe se copia al
 * portapapeles para pegarlo donde haga falta.
 */
@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val profile: DeviceProfile,
    private val engine: VisualizerEngine,
    visualizerSettings: VisualizerSettingsRepository,
) : ViewModel() {

    val gpu: StateFlow<GpuInfo?> = flow { emit(withContext(Dispatchers.Default) { GpuProbe.probe() }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val memory: StateFlow<MemorySnapshot> = flow {
        while (true) {
            emit(snapshot())
            delay(2_000)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), snapshot())

    val fps: StateFlow<Float> = engine.fps
    val quality: StateFlow<Float> = engine.quality
    val maxFps: StateFlow<Int> = visualizerSettings.settings
        .map { if (it.maxFps > 0) it.maxFps else profile.defaultMaxFps }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), profile.defaultMaxFps)

    private fun snapshot(): MemorySnapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val runtime = Runtime.getRuntime()
        return MemorySnapshot(
            availableMb = info.availMem / (1024L * 1024L),
            pssMb = Debug.getPss() / 1024L,
            heapUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L),
            heapMaxMb = runtime.maxMemory() / (1024L * 1024L),
        )
    }

    /** Informe en texto plano (sin traducir: es para enviarlo al desarrollador). */
    fun report(refreshRate: Float?): String {
        val p = profile
        val m = memory.value
        val g = gpu.value
        return buildString {
            appendLine("RadilloPlayerWave ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})")
            appendLine("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL} · hardware=${Build.HARDWARE} board=${Build.BOARD} device=${Build.DEVICE}")
            appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()} · 64 bits=${p.is64Bit}")
            appendLine("Android declarado: ${Build.VERSION.RELEASE} (API ${p.declaredApi}) · API real detectada: ${p.realApi}" + if (p.apiSpoofed) " · VERSIÓN FALSEADA" else "")
            appendLine("Perfil: " + if (p.lowEnd) "gama baja (${p.reasons.joinToString("; ")})" else "normal")
            appendLine("RAM total ${p.totalRamMb} MB · libre ${m.availableMb} MB · lowRam=${p.isLowRamDevice}")
            appendLine("App PSS ${m.pssMb} MB · heap ${m.heapUsedMb}/${m.heapMaxMb} MB")
            appendLine("Pantalla ${p.widthPx}x${p.heightPx} px · ${p.densityDpi} dpi · ${refreshRate?.let { String.format(Locale.ROOT, "%.0f Hz", it) } ?: "?"} · horizontal fija=${p.fixedLandscape}")
            appendLine("GPU: ${g?.renderer ?: "?"} · ${g?.vendor ?: ""} · ${g?.version ?: ""}")
            appendLine("Visualizador: ${fps.value.roundToInt()} fps de ${maxFps.value} · calidad ${(quality.value * 100).roundToInt()} %")
        }
    }

    fun copyReport(refreshRate: Float?) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("RadilloPlayerWave", report(refreshRate)))
        Toast.makeText(context, R.string.diag_copied, Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val memory by viewModel.memory.collectAsStateWithLifecycle()
    val gpu by viewModel.gpu.collectAsStateWithLifecycle()
    val fps by viewModel.fps.collectAsStateWithLifecycle()
    val quality by viewModel.quality.collectAsStateWithLifecycle()
    val maxFps by viewModel.maxFps.collectAsStateWithLifecycle()
    val profile = viewModel.profile
    val refreshRate = LocalView.current.display?.refreshRate

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.copyReport(refreshRate) }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.diag_copy))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (profile.lowEnd || profile.apiSpoofed) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (profile.lowEnd) {
                            Text(
                                stringResource(R.string.diag_profile_low_end, profile.reasons.joinToString("; ")),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (profile.apiSpoofed) {
                            Text(stringResource(R.string.diag_android_spoofed), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Section(stringResource(R.string.diag_section_device))
            DiagRow(stringResource(R.string.diag_model), "${Build.MANUFACTURER} ${Build.MODEL}")
            DiagRow(stringResource(R.string.diag_hardware), "${Build.HARDWARE} · ${Build.BOARD}")
            DiagRow(stringResource(R.string.diag_abis), Build.SUPPORTED_ABIS.joinToString())
            DiagRow(
                stringResource(R.string.diag_profile),
                if (profile.lowEnd) stringResource(R.string.diag_profile_low_end_short) else stringResource(R.string.diag_profile_normal),
            )

            Section(stringResource(R.string.diag_section_android))
            DiagRow(stringResource(R.string.diag_android_declared), stringResource(R.string.diag_api_format, Build.VERSION.RELEASE ?: "?", profile.declaredApi))
            DiagRow(stringResource(R.string.diag_android_real), stringResource(R.string.diag_api_level, profile.realApi))

            Section(stringResource(R.string.diag_section_memory))
            DiagRow(stringResource(R.string.diag_ram_total), stringResource(R.string.diag_mb, profile.totalRamMb))
            DiagRow(stringResource(R.string.diag_ram_available), stringResource(R.string.diag_mb, memory.availableMb))
            DiagRow(stringResource(R.string.diag_low_ram_flag), yesNo(profile.isLowRamDevice))
            DiagRow(stringResource(R.string.diag_app_pss), stringResource(R.string.diag_mb, memory.pssMb))
            DiagRow(stringResource(R.string.diag_heap), stringResource(R.string.diag_heap_format, memory.heapUsedMb, memory.heapMaxMb))

            Section(stringResource(R.string.diag_section_display))
            DiagRow(stringResource(R.string.diag_resolution), "${profile.widthPx} × ${profile.heightPx} px")
            DiagRow(stringResource(R.string.diag_density), "${profile.densityDpi} dpi")
            DiagRow(
                stringResource(R.string.diag_refresh),
                refreshRate?.let { String.format(Locale.getDefault(), "%.0f Hz", it) } ?: stringResource(R.string.diag_unavailable),
            )
            DiagRow(stringResource(R.string.diag_orientation_fixed), yesNo(profile.fixedLandscape))

            Section(stringResource(R.string.diag_section_gpu))
            val g = gpu
            DiagRow(stringResource(R.string.diag_renderer), g?.renderer ?: if (g == null) stringResource(R.string.diag_probing) else stringResource(R.string.diag_unavailable))
            DiagRow(stringResource(R.string.diag_gl_version), g?.version ?: "")

            Section(stringResource(R.string.diag_section_visualizer))
            DiagRow(stringResource(R.string.diag_fps), stringResource(R.string.diag_fps_format, fps, maxFps))
            DiagRow(stringResource(R.string.diag_quality), "${(quality * 100).roundToInt()} %")

            Text(
                stringResource(R.string.diag_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun yesNo(value: Boolean): String = stringResource(if (value) R.string.diag_yes else R.string.diag_no)

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 2.dp),
    )
    HorizontalDivider()
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.3f),
        )
    }
}
