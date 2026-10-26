package com.radillo.playerwave.ui.common

import android.widget.Toast
import androidx.fragment.app.Fragment
import com.radillo.playerwave.R
import com.radillo.playerwave.player.audiofx.SystemEqualizer

/**
 * Abre el ecualizador del sistema (en el estéreo: ecualizador, balance y fader del equipo). Si el
 * equipo no tiene ninguno, lo dice en vez de no hacer nada.
 */
fun Fragment.openSystemEqualizer() {
    val context = context ?: return
    if (!SystemEqualizer.open(requireActivity())) {
        Toast.makeText(context, R.string.equalizer_system_missing, Toast.LENGTH_LONG).show()
    }
}
