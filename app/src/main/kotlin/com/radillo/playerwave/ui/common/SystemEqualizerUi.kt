package com.radillo.playerwave.ui.common

import android.app.AlertDialog
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.radillo.playerwave.R
import com.radillo.playerwave.player.audiofx.SystemEqualizer

/**
 * Abre el ecualizador del sistema (en el estéreo: ecualizador, balance y fader del equipo). Si no
 * encuentra cuál es, deja elegir la app una vez y la recuerda.
 */
fun Fragment.openSystemEqualizer() {
    if (!isAdded) return
    if (!SystemEqualizer.open(requireActivity())) chooseSystemEqualizer(openAfter = true)
}

/**
 * Lista de apps para elegir cuál es el ecualizador del equipo (primero las que lo parecen).
 * [onChosen] se llama tras guardar la elección (por ejemplo, para refrescar Ajustes).
 */
fun Fragment.chooseSystemEqualizer(openAfter: Boolean, onChosen: () -> Unit = {}) {
    val context = context ?: return
    val apps = SystemEqualizer.candidates(context)
    if (apps.isEmpty()) {
        Toast.makeText(context, R.string.equalizer_system_missing, Toast.LENGTH_LONG).show()
        return
    }
    val labels = apps.map { if (it.likely) "★ ${it.app.label}" else it.app.label }.toTypedArray()
    val current = SystemEqualizer.chosen(context)?.let { c -> apps.indexOfFirst { it.app.component == c.component } } ?: -1
    AlertDialog.Builder(context)
        .setTitle(R.string.equalizer_choose_app)
        .setSingleChoiceItems(labels, current) { dialog, which ->
            dialog.dismiss()
            SystemEqualizer.choose(context, apps[which].app)
            onChosen()
            if (openAfter && isAdded && !SystemEqualizer.open(requireActivity())) {
                Toast.makeText(context, R.string.equalizer_system_missing, Toast.LENGTH_LONG).show()
            }
        }
        .setNegativeButton(R.string.action_cancel, null)
        .show()
}
