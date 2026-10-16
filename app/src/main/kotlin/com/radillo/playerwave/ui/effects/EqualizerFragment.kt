package com.radillo.playerwave.ui.effects

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.radillo.playerwave.R
import com.radillo.playerwave.databinding.FragmentEqualizerBinding
import com.radillo.playerwave.player.audiofx.AudioEffectsState
import com.radillo.playerwave.ui.common.collectWhenStarted
import com.radillo.playerwave.ui.common.dp
import com.radillo.playerwave.ui.common.onUserChange
import com.radillo.playerwave.ui.common.visibleIf
import com.radillo.playerwave.ui.navigator
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Ecualizador y refuerzo de graves del propio dispositivo. Si el equipo no ofrece un ecualizador
 * operativo se muestra un aviso en vez de controles sin efecto.
 */
@AndroidEntryPoint
class EqualizerFragment : Fragment(R.layout.fragment_equalizer) {

    private val viewModel: EqualizerViewModel by viewModels()
    private var binding: FragmentEqualizerBinding? = null
    private val bandSeeks = mutableListOf<SeekBar>()
    private val bandValues = mutableListOf<TextView>()
    private val presetChips = mutableListOf<TextView>()
    private var draggingBand = -1
    private var draggingBass = false
    private var builtFor: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentEqualizerBinding.bind(view)
        binding = b
        b.btnBack.setOnClickListener { navigator.back() }
        b.btnReset.setOnClickListener { viewModel.reset() }
        b.switchEnabled.setOnCheckedChangeListener { button, checked ->
            if (button.isPressed) viewModel.setEnabled(checked)
        }
        b.bassSeek.onUserChange(
            onChange = { p ->
                viewModel.previewBassBoost(p)
                b.bassValue.text = "${(p / 10f).roundToInt()} %"
            },
            onStart = { draggingBass = true },
            onStop = {
                draggingBass = false
                viewModel.persist()
            },
        )
        collectWhenStarted(viewModel.state, ::render)
    }

    override fun onDestroyView() {
        bandSeeks.clear()
        bandValues.clear()
        presetChips.clear()
        builtFor = null
        binding = null
        super.onDestroyView()
    }

    private fun render(state: AudioEffectsState) {
        val b = binding ?: return
        val message = when {
            !state.checked -> R.string.eq_needs_playback
            !state.available -> R.string.eq_unavailable
            else -> null
        }
        b.message.visibleIf(message != null)
        if (message != null) b.message.setText(message)
        b.controls.visibleIf(message == null)
        b.btnReset.visibleIf(message == null && state.enabled)
        if (message != null) return

        b.switchEnabled.isChecked = state.enabled
        buildIfNeeded(state)

        presetChips.forEachIndexed { i, chip ->
            chip.isSelected = state.presetIndex == i
            chip.isEnabled = state.enabled
        }
        val span = (state.maxLevelMb - state.minLevelMb).coerceAtLeast(1)
        state.bands.forEachIndexed { i, band ->
            val seek = bandSeeks.getOrNull(i) ?: return@forEachIndexed
            seek.isEnabled = state.enabled
            if (draggingBand != i) {
                seek.progress = ((band.levelMb - state.minLevelMb) * 1000 / span).coerceIn(0, 1000)
                bandValues[i].text = formatDb(band.levelMb)
            }
        }
        b.bassGroup.visibleIf(state.bassBoostSupported)
        b.bassSeek.isEnabled = state.enabled
        if (!draggingBass) {
            b.bassSeek.progress = state.bassBoostStrength.coerceIn(0, 1000)
            b.bassValue.text = "${(state.bassBoostStrength / 10f).roundToInt()} %"
        }
    }

    /** Crea las filas de bandas y los preajustes una sola vez por configuración del dispositivo. */
    private fun buildIfNeeded(state: AudioEffectsState) {
        val b = binding ?: return
        val signature = state.bands.joinToString { it.centerHz.toString() } + "|" + state.presets.joinToString()
        if (signature == builtFor) return
        builtFor = signature
        val inflater = LayoutInflater.from(requireContext())

        b.presets.removeAllViews()
        presetChips.clear()
        state.presets.forEachIndexed { index, name ->
            val chip = inflater.inflate(R.layout.view_chip, b.presets, false) as TextView
            chip.text = name
            chip.setOnClickListener { viewModel.usePreset(index) }
            b.presets.addView(chip)
            presetChips += chip
        }
        b.presetsLabel.visibleIf(state.presets.isNotEmpty())
        b.presetsScroll.visibleIf(state.presets.isNotEmpty())

        b.bands.removeAllViews()
        bandSeeks.clear()
        bandValues.clear()
        val span = (state.maxLevelMb - state.minLevelMb).coerceAtLeast(1)
        state.bands.forEachIndexed { i, band ->
            val header = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
            val label = TextView(requireContext(), null, 0, R.style.Text).apply {
                text = formatHz(band.centerHz)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val value = TextView(requireContext(), null, 0, R.style.Text_Secondary)
            header.addView(label)
            header.addView(value)
            val seek = SeekBar(requireContext()).apply {
                max = 1000
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, resources.getDimensionPixelSize(R.dimen.touch))
            }
            seek.onUserChange(
                onChange = { p ->
                    val mb = state.minLevelMb + p * span / 1000
                    value.text = formatDb(mb)
                    viewModel.previewBandLevel(band.index, mb)
                },
                onStart = { draggingBand = i },
                onStop = {
                    draggingBand = -1
                    viewModel.persist()
                },
            )
            header.setPadding(0, requireContext().dp(8), 0, 0)
            b.bands.addView(header)
            b.bands.addView(seek)
            bandSeeks += seek
            bandValues += value
        }
    }

    private fun formatHz(hz: Int): String =
        if (hz >= 1000) String.format(Locale.getDefault(), "%.1f kHz", hz / 1000f) else "$hz Hz"

    private fun formatDb(mb: Int): String = String.format(Locale.getDefault(), "%+.1f dB", mb / 100f)
}
