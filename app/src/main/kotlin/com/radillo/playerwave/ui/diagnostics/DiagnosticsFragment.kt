package com.radillo.playerwave.ui.diagnostics

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.radillo.playerwave.R
import com.radillo.playerwave.databinding.FragmentDiagnosticsBinding
import com.radillo.playerwave.ui.common.collectWhenStarted
import com.radillo.playerwave.ui.common.dp
import com.radillo.playerwave.ui.navigator
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Diagnóstico del dispositivo: RAM, Android real, GPU y fps del visualizador. Las filas se crean
 * una vez y solo se actualizan sus valores.
 */
@AndroidEntryPoint
class DiagnosticsFragment : Fragment(R.layout.fragment_diagnostics) {

    private val viewModel: DiagnosticsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentDiagnosticsBinding.bind(view)
        val p = viewModel.profile
        val refreshRate = view.display?.refreshRate
        b.btnBack.setOnClickListener { navigator.back() }
        b.btnCopy.setOnClickListener { viewModel.copyReport(refreshRate) }

        if (p.lowEnd) note(b.rows, getString(R.string.diag_profile_low_end, p.reasons.joinToString("; ")))
        if (p.apiSpoofed) note(b.rows, getString(R.string.diag_android_spoofed))

        section(b.rows, R.string.diag_section_device)
        row(b.rows, R.string.diag_model).text = "${Build.MANUFACTURER} ${Build.MODEL}"
        row(b.rows, R.string.diag_hardware).text = "${Build.HARDWARE} · ${Build.BOARD}"
        row(b.rows, R.string.diag_abis).text = Build.SUPPORTED_ABIS.joinToString()
        row(b.rows, R.string.diag_profile).setText(if (p.lowEnd) R.string.diag_profile_low_end_short else R.string.diag_profile_normal)

        section(b.rows, R.string.diag_section_android)
        row(b.rows, R.string.diag_android_declared).text = getString(R.string.diag_api_format, Build.VERSION.RELEASE ?: "?", p.declaredApi)
        row(b.rows, R.string.diag_android_real).text = getString(R.string.diag_api_level, p.realApi)

        section(b.rows, R.string.diag_section_memory)
        row(b.rows, R.string.diag_ram_total).text = getString(R.string.diag_mb, p.totalRamMb)
        val ramFree = row(b.rows, R.string.diag_ram_available)
        row(b.rows, R.string.diag_low_ram_flag).setText(if (p.isLowRamDevice) R.string.diag_yes else R.string.diag_no)
        val pss = row(b.rows, R.string.diag_app_pss)
        val heap = row(b.rows, R.string.diag_heap)

        section(b.rows, R.string.diag_section_display)
        row(b.rows, R.string.diag_resolution).text = "${p.widthPx} × ${p.heightPx} px"
        row(b.rows, R.string.diag_density).text = "${p.densityDpi} dpi"
        row(b.rows, R.string.diag_refresh).text =
            refreshRate?.let { String.format(Locale.getDefault(), "%.0f Hz", it) } ?: getString(R.string.diag_unavailable)
        row(b.rows, R.string.diag_orientation_fixed).setText(if (p.fixedLandscape) R.string.diag_yes else R.string.diag_no)

        section(b.rows, R.string.diag_section_gpu)
        val renderer = row(b.rows, R.string.diag_renderer)
        val glVersion = row(b.rows, R.string.diag_gl_version)

        section(b.rows, R.string.diag_section_visualizer)
        val fps = row(b.rows, R.string.diag_fps)
        val quality = row(b.rows, R.string.diag_quality)

        TextView(requireContext(), null, 0, R.style.Text_Secondary).apply {
            setText(R.string.diag_hint)
            setPadding(0, requireContext().dp(16), 0, 0)
            b.rows.addView(this)
        }

        collectWhenStarted(viewModel.memory) { m ->
            ramFree.text = getString(R.string.diag_mb, m.availableMb)
            pss.text = getString(R.string.diag_mb, m.pssMb)
            heap.text = getString(R.string.diag_heap_format, m.heapUsedMb, m.heapMaxMb)
        }
        collectWhenStarted(viewModel.gpu) { g ->
            renderer.text = when {
                g == null -> getString(R.string.diag_probing)
                g.renderer == null -> getString(R.string.diag_unavailable)
                else -> g.renderer
            }
            glVersion.text = g?.version.orEmpty()
        }
        collectWhenStarted(viewModel.fps) { f -> fps.text = getString(R.string.diag_fps_format, f, viewModel.maxFps.value) }
        collectWhenStarted(viewModel.maxFps) { max -> fps.text = getString(R.string.diag_fps_format, viewModel.fps.value, max) }
        collectWhenStarted(viewModel.quality) { q -> quality.text = "${(q * 100).roundToInt()} %" }
    }

    private fun section(container: LinearLayout, @StringRes title: Int) {
        TextView(requireContext(), null, 0, R.style.Text_Section).apply {
            setText(title)
            container.addView(this)
        }
    }

    private fun note(container: LinearLayout, text: String) {
        TextView(requireContext(), null, 0, R.style.Text).apply {
            this.text = text
            val pad = requireContext().dp(12)
            setPadding(pad, pad, pad, pad)
            setBackgroundResource(R.drawable.bg_button_secondary)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = requireContext().dp(8)
            layoutParams = lp
            container.addView(this)
        }
    }

    /** Añade una fila «etiqueta — valor» y devuelve el `TextView` del valor. */
    private fun row(container: LinearLayout, @StringRes label: Int): TextView {
        val line = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, requireContext().dp(4), 0, requireContext().dp(4))
        }
        val name = TextView(requireContext(), null, 0, R.style.Text).apply {
            setText(label)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val value = TextView(requireContext(), null, 0, R.style.Text_Secondary).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f)
        }
        line.addView(name)
        line.addView(value)
        container.addView(line)
        return value
    }
}
