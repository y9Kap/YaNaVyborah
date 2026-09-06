package org.yanavybori.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.yanavybori.core.ui.YaNaVyborahTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as YaNaVyborahApplication).container
        setContent {
            YaNaVyborahTheme {
                YaNaVyborahRoot(container)
            }
        }
    }
}
