package com.radillo.playerwave.core.device

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.util.DisplayMetrics

/**
 * Perfil del dispositivo, calculado una vez al arrancar. Decide los valores por defecto de la app
 * (tope de fps y densidad del visualizador, tamaño de las carátulas, orientación fija) sin que
 * el usuario tenga que configurar nada.
 *
 * `lowEnd` es verdadero en hardware como el autoestéreo MTK 8227L: 1 GB de RAM, CPU de 32 bits,
 * GPU Mali-450. Cada señal basta por sí sola; `reasons` las enumera para la pantalla de diagnóstico.
 */
class DeviceProfile(
    val totalRamMb: Long,
    val isLowRamDevice: Boolean,
    val maxHeapMb: Long,
    val is64Bit: Boolean,
    val declaredApi: Int,
    val realApi: Int,
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val hasAccelerometer: Boolean,
    val isCarUiMode: Boolean,
    val hardwareHints: List<String>,
) {
    /** Motivos por los que se considera gama baja (vacío si no lo es). */
    val reasons: List<String> = buildList {
        if (isLowRamDevice) add("Android marca el dispositivo como de poca memoria")
        if (totalRamMb in 1..1_600) add("RAM total de $totalRamMb MB")
        if (maxHeapMb in 1..192) add("heap máximo de $maxHeapMb MB")
        if (!is64Bit) add("CPU de 32 bits")
        if (hardwareHints.isNotEmpty()) add("hardware ${hardwareHints.joinToString()}")
    }

    val lowEnd: Boolean get() = reasons.isNotEmpty()

    /** `true` si el sistema declara una versión de Android mayor que la real. */
    val apiSpoofed: Boolean get() = declaredApi > realApi

    /** Pantalla de coche: sin acelerómetro o modo UI de automóvil; se fija la orientación horizontal. */
    val fixedLandscape: Boolean get() = isCarUiMode || (!hasAccelerometer && widthPx > heightPx)

    /** Fotogramas por segundo del visualizador por defecto. */
    val defaultMaxFps: Int get() = if (lowEnd) 30 else 60

    /** Factor que multiplica la densidad de las escenas (partículas, líneas, anillos). */
    val visualizerDensityScale: Float get() = if (lowEnd) 0.6f else 1f

    /** Calidad mínima a la que puede bajar el gobernador. */
    val minQuality: Float get() = if (lowEnd) 0.25f else 0.3f

    /** Lado máximo, en píxeles, al que se decodifica una carátula grande. */
    val artworkMaxPx: Int get() = if (lowEnd) 256 else 512

    companion object {
        private val LOW_END_HARDWARE = listOf("8227", "ac8227", "mt8227", "mt6580", "mt8321", "sc7731", "rk3326", "px30")

        fun detect(context: Context): DeviceProfile {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memory = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
            val totalRamMb = if (memory.totalMem > 0) memory.totalMem / (1024L * 1024L) else 0L
            val isLowRam = am?.isLowRamDevice ?: false
            val maxHeapMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L)
            val is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
            val metrics: DisplayMetrics = context.resources.displayMetrics
            val pm = context.packageManager
            val hasAccelerometer = pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)
            val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
            val fields = listOf(Build.HARDWARE, Build.BOARD, Build.DEVICE, Build.MODEL, Build.PRODUCT)
                .map { it.orEmpty().lowercase() }
            val hints = LOW_END_HARDWARE.filter { hint -> fields.any { it.contains(hint) } }.distinct()
            return DeviceProfile(
                totalRamMb = totalRamMb,
                isLowRamDevice = isLowRam,
                maxHeapMb = maxHeapMb,
                is64Bit = is64Bit,
                declaredApi = Build.VERSION.SDK_INT,
                realApi = RealApiLevel.detected,
                widthPx = metrics.widthPixels,
                heightPx = metrics.heightPixels,
                densityDpi = metrics.densityDpi,
                hasAccelerometer = hasAccelerometer,
                isCarUiMode = uiMode == Configuration.UI_MODE_TYPE_CAR,
                hardwareHints = hints,
            )
        }
    }
}
