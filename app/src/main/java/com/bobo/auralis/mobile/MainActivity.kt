package com.bobo.auralis.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobo.auralis.mobile.ui.demo.AuralisDemoApp
import com.bobo.auralis.mobile.ui.theme.AppearancePreferencesRepository
import com.bobo.auralis.mobile.ui.theme.AlbumLayoutAnimationStyle
import com.bobo.auralis.mobile.ui.theme.AlbumLayoutMode
import com.bobo.auralis.mobile.ui.theme.AuralisTheme
import com.bobo.auralis.mobile.ui.theme.AuralisThemeMode
import com.bobo.auralis.mobile.ui.theme.usesDarkColors
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val appearancePreferences by lazy {
        AppearancePreferencesRepository(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by appearancePreferences.themeMode.collectAsStateWithLifecycle(
                initialValue = AuralisThemeMode.System,
            )
            val albumLayoutMode by appearancePreferences.albumLayoutMode.collectAsStateWithLifecycle(
                initialValue = AlbumLayoutMode.Grid2,
            )
            val albumLayoutAnimationStyle by appearancePreferences.albumLayoutAnimationStyle
                .collectAsStateWithLifecycle(initialValue = AlbumLayoutAnimationStyle.Fade)
            val coroutineScope = rememberCoroutineScope()
            val useDarkColors = themeMode.usesDarkColors(isSystemInDarkTheme())
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !useDarkColors
                    isAppearanceLightNavigationBars = !useDarkColors
                }
            }
            AuralisTheme(themeMode = themeMode) {
                AuralisDemoApp(
                    themeMode = themeMode,
                    albumLayoutMode = albumLayoutMode,
                    albumLayoutAnimationStyle = albumLayoutAnimationStyle,
                    onThemeModeSelected = { selectedMode ->
                        coroutineScope.launch {
                            appearancePreferences.setThemeMode(selectedMode)
                        }
                    },
                    onAlbumLayoutModeSelected = { selectedMode ->
                        coroutineScope.launch {
                            appearancePreferences.setAlbumLayoutMode(selectedMode)
                        }
                    },
                    onAlbumLayoutAnimationStyleSelected = { selectedStyle ->
                        coroutineScope.launch {
                            appearancePreferences.setAlbumLayoutAnimationStyle(selectedStyle)
                        }
                    },
                )
            }
        }
    }
}
