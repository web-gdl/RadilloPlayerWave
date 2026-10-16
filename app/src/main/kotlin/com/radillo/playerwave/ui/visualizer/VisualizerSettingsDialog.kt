package com.radillo.playerwave.ui.visualizer

import android.app.AlertDialog
import android.view.LayoutInflater
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.radillo.playerwave.R
import com.radillo.playerwave.core.model.VisualizerSettings
import com.radillo.playerwave.databinding.DialogVisualizerSettingsBinding
import com.radillo.playerwave.ui.common.onUserChange
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Ajustes del visualizador en un diálogo de plataforma. Los deslizadores previsualizan al moverse
 * y se guardan al soltarlos.
 */
object VisualizerSettingsDialog {

    fun show(fragment: Fragment, viewModel: VisualizerViewModel) {
        val context = fragment.requireContext()
        val b = DialogVisualizerSettingsBinding.inflate(LayoutInflater.from(context))
        var draft = viewModel.settings.value

        fun bindSlider(
            seek: SeekBar,
            label: TextView,
            range: ClosedFloatingPointRange<Float>,
            get: (VisualizerSettings) -> Float,
            set: (VisualizerSettings, Float) -> VisualizerSettings,
            format: (Float) -> String = { String.format(Locale.ROOT, "%.2f×", it) },
        ) {
            val span = range.endInclusive - range.start
            fun toProgress(v: Float) = (((v - range.start) / span) * 1000f).roundToInt().coerceIn(0, 1000)
            fun fromProgress(p: Int) = range.start + span * p / 1000f
            seek.progress = toProgress(get(draft))
            label.text = format(get(draft))
            seek.onUserChange(
                onChange = { p ->
                    val v = fromProgress(p)
                    draft = set(draft, v)
                    label.text = format(v)
                    viewModel.preview(draft)
                },
                onStop = {
                    val v = get(draft)
                    viewModel.commit { set(it, v) }
                },
            )
        }

        bindSlider(b.seekSensitivity, b.valSensitivity, VisualizerSettings.SENSITIVITY_RANGE, { it.sensitivity }, { s, v -> s.copy(sensitivity = v) })
        bindSlider(b.seekIntensity, b.valIntensity, VisualizerSettings.INTENSITY_RANGE, { it.intensity }, { s, v -> s.copy(intensity = v) })
        bindSlider(b.seekSpeed, b.valSpeed, VisualizerSettings.SPEED_RANGE, { it.speed }, { s, v -> s.copy(speed = v) })
        bindSlider(b.seekDensity, b.valDensity, VisualizerSettings.DENSITY_RANGE, { it.density }, { s, v -> s.copy(density = v) })
        val sync = VisualizerSettings.SYNC_RANGE_MS
        bindSlider(
            b.seekSync, b.valSync, sync.first.toFloat()..sync.last.toFloat(),
            { it.syncOffsetMs.toFloat() }, { s, v -> s.copy(syncOffsetMs = v.roundToInt()) },
            format = { context.getString(R.string.vis_sync_value, it.roundToInt()) },
        )

        val inflater = LayoutInflater.from(context)
        val paletteChips = viewModel.palettes.map { palette ->
            (inflater.inflate(R.layout.view_chip, b.paletteChips, false) as TextView).apply {
                setText(paletteLabelRes(palette.id))
                isSelected = palette.id == draft.paletteId
                tag = palette.id
                b.paletteChips.addView(this)
            }
        }
        paletteChips.forEach { chip ->
            chip.setOnClickListener {
                val id = chip.tag as String
                paletteChips.forEach { it.isSelected = it === chip }
                draft = draft.copy(paletteId = id)
                viewModel.selectPalette(id)
            }
        }

        val fpsChips = VisualizerSettings.MAX_FPS_OPTIONS.map { option ->
            (inflater.inflate(R.layout.view_chip, b.fpsChips, false) as TextView).apply {
                text = if (option == 0) {
                    context.getString(R.string.vis_fps_auto, viewModel.profile.defaultMaxFps)
                } else {
                    context.getString(R.string.vis_fps_value, option)
                }
                isSelected = option == draft.maxFps
                tag = option
                b.fpsChips.addView(this)
            }
        }
        fpsChips.forEach { chip ->
            chip.setOnClickListener {
                fpsChips.forEach { it.isSelected = it === chip }
                viewModel.setMaxFps(chip.tag as Int)
            }
        }

        b.switchKeepScreenOn.isChecked = draft.keepScreenOn
        b.switchKeepScreenOn.setOnCheckedChangeListener { _, checked -> viewModel.setKeepScreenOn(checked) }
        b.switchAutoQuality.isChecked = draft.autoQuality
        b.switchAutoQuality.setOnCheckedChangeListener { _, checked -> viewModel.setAutoQuality(checked) }

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.vis_settings)
            .setView(b.root)
            .setPositiveButton(R.string.action_close, null)
            .setNeutralButton(R.string.vis_reset) { _, _ -> viewModel.resetTuning() }
            .create()

        // Muestra el detalle vigente del gobernador mientras el diálogo está abierto.
        val job = fragment.viewLifecycleOwner.lifecycleScope.launch {
            viewModel.quality.collect { q ->
                b.autoQualityHint.text = if (b.switchAutoQuality.isChecked && q < 0.995f) {
                    context.getString(R.string.vis_auto_quality_current, (q * 100f).roundToInt())
                } else {
                    context.getString(R.string.vis_auto_quality_hint)
                }
            }
        }
        dialog.setOnDismissListener { job.cancel() }
        dialog.show()
    }
}
