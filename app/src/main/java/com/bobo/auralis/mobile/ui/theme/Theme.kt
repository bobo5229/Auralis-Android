package com.bobo.auralis.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalAuralisExtendedColors = staticCompositionLocalOf {
    AuralisColorSystems.default.light.extended
}

object AuralisThemeTokens {
    val colors: AuralisExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAuralisExtendedColors.current
}

@Composable
fun AuralisTheme(
    themeMode: AuralisThemeMode = AuralisThemeMode.System,
    colorSystem: AuralisColorSystem = AuralisColorSystems.default,
    content: @Composable () -> Unit,
) {
    val systemInDarkTheme = isSystemInDarkTheme()
    val darkTheme = themeMode.usesDarkColors(systemInDarkTheme)
    val spec = if (darkTheme) colorSystem.dark else colorSystem.light
    CompositionLocalProvider(LocalAuralisExtendedColors provides spec.extended) {
        MaterialTheme(
            colorScheme = spec.material,
            typography = Typography,
            content = content,
        )
    }
}

internal fun AuralisThemeMode.usesDarkColors(systemInDarkTheme: Boolean): Boolean =
    when (this) {
        AuralisThemeMode.System -> systemInDarkTheme
        AuralisThemeMode.Light -> false
        AuralisThemeMode.Dark -> true
    }
