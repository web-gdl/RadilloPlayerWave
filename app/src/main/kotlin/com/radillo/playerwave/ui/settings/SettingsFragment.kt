package com.radillo.playerwave.ui.settings

import android.app.AlertDialog
import android.widget.Toast
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.radillo.playerwave.BuildConfig
import com.radillo.playerwave.R
import com.radillo.playerwave.core.model.ThemeMode
import com.radillo.playerwave.core.settings.PlaybackSettings
import com.radillo.playerwave.databinding.FragmentSettingsBinding
import com.radillo.playerwave.ui.common.collectWhenStarted
import com.radillo.playerwave.ui.diagnostics.DiagnosticsFragment
import com.radillo.playerwave.ui.effects.EqualizerFragment
import com.radillo.playerwave.ui.library.scanSummary
import com.radillo.playerwave.ui.navigator
import dagger.hilt.android.AndroidEntryPoint

/** Bibliotecas de terceros incluidas en el APK (ver docs/DEPENDENCIES.md). */
private val ThirdPartyLicenses = listOf(
    "Kotlin y kotlinx.coroutines" to "Apache 2.0",
    "AndroidX (Core, Fragment, RecyclerView, Lifecycle, Room, Palette)" to "Apache 2.0",
    "AndroidX Media3 (ExoPlayer, MediaSession)" to "Apache 2.0",
    "Dagger Hilt" to "Apache 2.0",
    "Iconos derivados de Material Icons" to "Apache 2.0",
)

@AndroidEntryPoint
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val viewModel: SettingsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentSettingsBinding.bind(view)
        b.btnBack.setOnClickListener { navigator.back() }
        b.rowEqualizer.setOnClickListener { navigator.push(EqualizerFragment()) }
        b.rowDiagnostics.setOnClickListener { navigator.push(DiagnosticsFragment()) }
        b.rowLicenses.setOnClickListener { showLicenses() }
        b.rowRescan.setOnClickListener { viewModel.rescan() }
        b.switchKeepPlaying.setOnCheckedChangeListener { _, checked -> if (!binding) viewModel.setKeepPlaying(checked) }
        collectWhenStarted(viewModel.keepPlaying) { bind { b.switchKeepPlaying.isChecked = it } }
        b.rowCrossfade.setOnClickListener { chooseCrossfade() }
        collectWhenStarted(viewModel.crossfadeSeconds) { seconds ->
            b.crossfadeValue.text = crossfadeLabel(seconds)
            // Sin crossfade el interruptor no hace nada: se ve apagado y no se puede tocar.
            b.switchCrossfadeSkip.isEnabled = seconds > 0
            b.switchCrossfadeSkip.alpha = if (seconds > 0) 1f else 0.5f
        }
        b.switchCrossfadeSkip.setOnCheckedChangeListener { _, checked -> if (!binding) viewModel.setCrossfadeOnSkip(checked) }
        collectWhenStarted(viewModel.crossfadeOnSkip) { bind { b.switchCrossfadeSkip.isChecked = it } }
        collectWhenStarted(viewModel.isScanning) { scanning ->
            b.rowRescan.isEnabled = !scanning
            b.rowRescan.alpha = if (scanning) 0.5f else 1f
            b.rescanHint.setText(if (scanning) R.string.empty_scanning else R.string.settings_rescan_hint)
        }
        collectWhenStarted(viewModel.scanResults) { result ->
            val ctx = requireContext()
            Toast.makeText(ctx, ctx.scanSummary(result), Toast.LENGTH_SHORT).show()
        }
        b.versionText.text = getString(R.string.app_name) + " · " + getString(R.string.settings_version, BuildConfig.VERSION_NAME)
        b.profileText.setText(if (viewModel.profile.lowEnd) R.string.diag_profile_low_end_short else R.string.diag_profile_normal)

        val themeViews = mapOf(ThemeMode.SYSTEM to b.themeSystem, ThemeMode.LIGHT to b.themeLight, ThemeMode.DARK to b.themeDark)
        themeViews.forEach { (mode, v) -> v.setOnClickListener { viewModel.setThemeMode(mode) } }
        b.switchArtworkColors.setOnCheckedChangeListener { _, checked -> if (!binding) viewModel.setArtworkColors(checked) }
        b.switchLandscape.setOnCheckedChangeListener { _, checked -> if (!binding) viewModel.setLockLandscape(checked) }

        collectWhenStarted(viewModel.settings) { s ->
            themeViews.forEach { (mode, v) -> v.isSelected = mode == s.themeMode }
            bind {
                b.switchArtworkColors.isChecked = s.artworkColors
                b.switchLandscape.isChecked = s.lockLandscape ?: viewModel.profile.fixedLandscape
            }
        }
    }

    /**
     * Mientras se reflejan los valores guardados, los cambios de los interruptores no se toman
     * como del usuario. (Antes se usaba `isPressed`, que falla con la rueda o el mando del equipo:
     * al soltar la tecla el estado «pulsado» se limpia antes del clic.)
     */
    private var binding = false

    private inline fun bind(block: () -> Unit) {
        binding = true
        try {
            block()
        } finally {
            binding = false
        }
    }

    private fun crossfadeLabel(seconds: Int): String =
        if (seconds <= 0) getString(R.string.settings_crossfade_off) else getString(R.string.settings_crossfade_seconds, seconds)

    /** Lista corta de duraciones (apagado, 2…12 s): se elige de un toque y se cierra sola. */
    private fun chooseCrossfade() {
        val options = PlaybackSettings.CROSSFADE_OPTIONS
        val labels = options.map(::crossfadeLabel).toTypedArray()
        val current = options.indexOf(viewModel.crossfadeSeconds.value).coerceAtLeast(0)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_crossfade)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                viewModel.setCrossfadeSeconds(options[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showLicenses() {
        val text = buildString {
            append(getString(R.string.settings_licenses_intro))
            append("\n\n")
            ThirdPartyLicenses.forEach { (name, license) -> append("• ").append(name).append(" — ").append(license).append('\n') }
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_licenses)
            .setMessage(text)
            .setPositiveButton(R.string.action_close, null)
            .show()
    }
}
