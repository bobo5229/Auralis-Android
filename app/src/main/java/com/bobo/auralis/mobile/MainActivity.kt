package com.bobo.auralis.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bobo.auralis.mobile.debug.SafDebugScreen
import com.bobo.auralis.mobile.ui.theme.AuralisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AuralisTheme {
                // Technical Spike entry point. Replaced by the product UI after the spike passes.
                SafDebugScreen()
            }
        }
    }
}