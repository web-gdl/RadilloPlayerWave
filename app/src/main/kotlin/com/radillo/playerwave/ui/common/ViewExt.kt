package com.radillo.playerwave.ui.common

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Recoge `flow` mientras la vista del fragment está al menos en STARTED (se para en segundo plano). */
fun <T> Fragment.collectWhenStarted(flow: Flow<T>, block: (T) -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect { block(it) }
        }
    }
}

fun View.visibleIf(visible: Boolean) {
    val v = if (visible) View.VISIBLE else View.GONE
    if (visibility != v) visibility = v
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

/** Diálogo con un campo de texto (crear o renombrar lista). */
fun Context.showTextInputDialog(
    @StringRes title: Int,
    initial: String,
    @StringRes confirm: Int,
    @StringRes hint: Int,
    onConfirm: (String) -> Unit,
) {
    val field = EditText(this).apply {
        setText(initial)
        setSelection(initial.length)
        setHint(hint)
        maxLines = 1
        setSingleLine(true)
    }
    val container = FrameLayout(this).apply {
        val pad = dp(24)
        setPadding(pad, dp(8), pad, 0)
        addView(field)
    }
    AlertDialog.Builder(this)
        .setTitle(title)
        .setView(container)
        .setPositiveButton(confirm) { _, _ ->
            val text = field.text.toString().trim()
            if (text.isNotEmpty()) onConfirm(text)
        }
        .setNegativeButton(com.radillo.playerwave.R.string.action_cancel, null)
        .show()
}

/** Escucha simplificada de `SeekBar`: cambios del usuario y fin del arrastre. */
fun SeekBar.onUserChange(onChange: (Int) -> Unit, onStop: () -> Unit = {}, onStart: () -> Unit = {}) {
    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser) onChange(progress)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) = onStart()
        override fun onStopTrackingTouch(seekBar: SeekBar) = onStop()
    })
}
