package com.bobo.auralis.mobile.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AuralisThemeModeTest {
    @Test
    fun knownKeysResolveToTheirThemeModes() {
        AuralisThemeMode.entries.forEach { mode ->
            assertEquals(mode, AuralisThemeMode.fromKey(mode.key))
        }
    }

    @Test
    fun missingOrUnknownKeysFallBackToSystem() {
        assertEquals(AuralisThemeMode.System, AuralisThemeMode.fromKey(null))
        assertEquals(AuralisThemeMode.System, AuralisThemeMode.fromKey("unknown"))
    }

    @Test
    fun explicitModesOverrideTheSystemAppearance() {
        assertEquals(false, AuralisThemeMode.Light.usesDarkColors(systemInDarkTheme = true))
        assertEquals(true, AuralisThemeMode.Dark.usesDarkColors(systemInDarkTheme = false))
        assertEquals(true, AuralisThemeMode.System.usesDarkColors(systemInDarkTheme = true))
        assertEquals(false, AuralisThemeMode.System.usesDarkColors(systemInDarkTheme = false))
    }
}
