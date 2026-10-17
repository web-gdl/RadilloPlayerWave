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
import com.radillo.playerwave.databinding.FragmentSettingsBinding
import com.radillo.playerwave.ui.common.collectWhenStarted
import com.radillo.playerwave.ui.diagnostics.DiagnosticsFragment
import com.radillo.playerwave.ui.effects.EqualizerFragment
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
        b.switchKeepPlaying.setOnCheckedChangeListener { button, checked -> if (button.isPressed) viewModel.setKeepPlaying(checked) }
        collectWhenStarted(viewModel.keepPlaying) { b.switchKeepPlaying.isChecked = it }
        collectWhenStarted(viewModel.isScanning) { scanning ->
            b.rowRescan.isEnabled = !scanning
            b.rowRescan.alpha = if (scanning) 0.5f else 1f
            b.rescanHint.setText(if (scanning) R.string.empty_scanning else R.string.settings_rescan_hint)
        }
        collectWhenStarted(viewModel.scanResults) { result ->
            val parts = buildList {
                if (result.added > 0) add(resources.getQuantityString(R.plurals.scan_added, result.added, result.added))
                if (result.removed > 0) add(resources.getQuantityString(R.plurals.scan_removed, result.removed, result.removed))
            }
            Toast.makeText(requireContext(), if (parts.isEmpty()) getString(R.string.scan_none) else parts.joinToString(" · "), Toast.LENGTH_SHORT).show()
        }
        b.versionText.text = getString(R.string.app_name) + " · " + getString(R.string.settings_version, BuildConfig.VERSION_NAME)
        b.profileText.setText(if (viewModel.profile.lowEnd) R.string.diag_profile_low_end_short else R.string.diag_profile_normal)

        val themeViews = mapOf(ThemeMode.SYSTEM to b.themeSystem, ThemeMode.LIGHT to b.themeLight, ThemeMode.DARK to b.themeDark)
        themeViews.forEach { (mode, v) -> v.setOnClickListener { viewModel.setThemeMode(mode) } }
        b.switchArtworkColors.setOnCheckedChangeListener { button, checked -> if (button.isPressed) viewModel.setArtworkColors(checked) }
        b.switchLandscape.setOnCheckedChangeListener { button, checked -> if (button.isPressed) viewModel.setLockLandscape(checked) }

        collectWhenStarted(viewModel.settings) { s ->
            themeViews.forEach { (mode, v) -> v.isSelected = mode == s.themeMode }
            b.switchArtworkColors.isChecked = s.artworkColors
            b.switchLandscape.isChecked = s.lockLandscape ?: viewModel.profile.fixedLandscape
        }
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
