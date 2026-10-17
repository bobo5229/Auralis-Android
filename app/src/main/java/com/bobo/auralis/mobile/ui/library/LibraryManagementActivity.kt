package com.bobo.auralis.mobile.ui.library

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.bobo.auralis.mobile.ui.theme.usesDarkColors
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobo.auralis.mobile.AuralisApplication
import com.bobo.auralis.mobile.ui.theme.AppearancePreferencesRepository
import com.bobo.auralis.mobile.ui.theme.AuralisTheme
import com.bobo.auralis.mobile.ui.theme.AuralisThemeMode

class LibraryManagementActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val management = (application as AuralisApplication).libraryManagement
        val appearance = AppearancePreferencesRepository(applicationContext)
        setContent {
            val mode by appearance.themeMode.collectAsStateWithLifecycle(AuralisThemeMode.System)
            val dark = mode.usesDarkColors(isSystemInDarkTheme())
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            AuralisTheme(themeMode = mode) {
                LibraryManagementRoute(management, onBack = ::finish)
            }
        }
    }
}
