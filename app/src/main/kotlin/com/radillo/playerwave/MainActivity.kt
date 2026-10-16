package com.radillo.playerwave

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.radillo.playerwave.ui.RadilloApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // El tema (claro/oscuro y acento de la carátula) se decide dentro de RadilloApp
        // a partir de los ajustes persistidos.
        setContent { RadilloApp() }
    }
}
