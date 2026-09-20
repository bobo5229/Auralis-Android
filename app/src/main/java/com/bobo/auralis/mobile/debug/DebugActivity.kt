package com.bobo.auralis.mobile.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bobo.auralis.mobile.ui.theme.AuralisTheme

/** Internal entry point that keeps the Technical Spike inspection tools out of the product UI. */
class DebugActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AuralisTheme { SafDebugScreen() }
        }
    }
}
