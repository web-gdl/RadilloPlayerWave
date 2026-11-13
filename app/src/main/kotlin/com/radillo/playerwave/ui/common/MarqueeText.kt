package com.radillo.playerwave.ui.common

import android.widget.TextView

/**
 * Pone un texto que, si no cabe, se desplaza como marquesina (el texto corto queda quieto).
 * La marquesina de Android solo corre con la vista «seleccionada»; y solo se reasigna el texto si
 * cambió, porque cada `setText` reinicia el desplazamiento.
 */
fun TextView.setMarqueeText(value: CharSequence) {
    if (!isSelected) isSelected = true
    if (text.toString() != value.toString()) text = value
}
