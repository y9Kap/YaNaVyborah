package org.yanavybori.app

import android.os.Bundle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import org.yanavybori.core.ui.AndroidPlatformUi
import org.yanavybori.core.ui.LocalPlatformUi
import org.yanavybori.shared.YaNaVyborahRoot
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as YaNaVyborahApplication).container
        setContent {
            val platform = remember { AndroidPlatformUi(this@MainActivity) }
            CompositionLocalProvider(LocalPlatformUi provides platform) {
                YaNaVyborahRoot(container.shared)
            }
        }
    }
}
