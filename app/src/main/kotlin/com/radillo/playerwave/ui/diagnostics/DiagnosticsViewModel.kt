package com.radillo.playerwave.ui.diagnostics

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radillo.playerwave.BuildConfig
import com.radillo.playerwave.R
import com.radillo.playerwave.core.device.CarSignals
import com.radillo.playerwave.core.device.DeviceProfile
import com.radillo.playerwave.core.device.GpuInfo
import com.radillo.playerwave.core.device.GpuProbe
import com.radillo.playerwave.core.diagnostics.EventLog
import com.radillo.playerwave.core.diagnostics.RenderMode
import com.radillo.playerwave.data.settings.VisualizerSettingsRepository
import com.radillo.playerwave.player.jancar.JancarStatus
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
            appendLine("Dibujo: elegido ${RenderMode.choice} · efectivo ${if (RenderMode.useGpu()) "GPU" else "CPU"}" + if (RenderMode.forcedCpuAfterCrash) " · CPU forzada tras un cierre" else "")
            appendLine("Estéreo: ${JancarStatus.summary()}")
            appendLine("Señales del auto: ${CarSignals.summary()}")
            appendLine()
            appendLine("— Registro —")
            EventLog.entries().forEach { appendLine(it) }
            EventLog.lastCrash()?.let { crash ->
                appendLine()
                appendLine("— Último cierre inesperado —")
                appendLine(crash.lines().take(40).joinToString("\n"))
            }
        }
    }

    fun copyReport(refreshRate: Float?) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("RadilloPlayerWave", report(refreshRate)))
        Toast.makeText(context, R.string.diag_copied, Toast.LENGTH_SHORT).show()
    }
}
