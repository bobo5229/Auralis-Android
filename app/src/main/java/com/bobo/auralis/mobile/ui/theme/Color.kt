package com.bobo.auralis.mobile.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private object SteelBluePrimitives {
    val LightCanvas = Color(0xFFF5F6F8)
    val LightSurface = Color(0xFFFBFCFE)
    val LightSurfaceLowest = Color(0xFFFFFFFF)
    val LightSurfaceLow = Color(0xFFF5F6F8)
    val LightSurfaceContainer = Color(0xFFEFF2F5)
    val LightSurfaceRaised = Color(0xFFE9EDF2)
    val LightSurfaceHighest = Color(0xFFE2E7EC)
    val LightTextPrimary = Color(0xFF17191D)
    val LightTextSecondary = Color(0xFF626872)
    val LightTextTertiary = Color(0xFF7A8089)
    val LightDisabled = Color(0xFFA4AAB2)
    val LightDivider = Color(0xFFD9DEE5)
    val LightAccent = Color(0xFF3E628F)
    val LightAccentSoft = Color(0xFFDFE8F3)

    val DarkCanvas = Color(0xFF0D0F12)
    val DarkSurface = Color(0xFF15181C)
    val DarkSurfaceLowest = Color(0xFF090B0D)
    val DarkSurfaceLow = Color(0xFF111418)
    val DarkSurfaceContainer = Color(0xFF171A1F)
    val DarkSurfaceHigh = Color(0xFF1B1F24)
    val DarkSurfaceRaised = Color(0xFF20242A)
    val DarkSurfaceBright = Color(0xFF2A2E35)
    val DarkTextPrimary = Color(0xFFF1F3F5)
    val DarkTextSecondary = Color(0xFFA6ABB3)
    val DarkTextTertiary = Color(0xFF858B94)
    val DarkDisabled = Color(0xFF676D75)
    val DarkDivider = Color(0xFF343941)
    val DarkAccent = Color(0xFF91B8E6)
    val DarkAccentSoft = Color(0xFF26384F)
}

data class AuralisExtendedColors(
    val contentTertiary: Color,
    val contentDisabled: Color,
    val glassLiquidTint: Color,
    val glassFrostedTint: Color,
)

internal data class AuralisThemeSpec(
    val material: ColorScheme,
    val extended: AuralisExtendedColors,
)

class AuralisColorSystem internal constructor(
    val key: String,
    internal val light: AuralisThemeSpec,
    internal val dark: AuralisThemeSpec,
)

object AuralisColorSystems {
    val SteelBlue = AuralisColorSystem(
        key = "steel_blue",
        light = AuralisThemeSpec(
            material = lightColorScheme(
                primary = SteelBluePrimitives.LightAccent,
                onPrimary = Color.White,
                primaryContainer = SteelBluePrimitives.LightAccentSoft,
                onPrimaryContainer = Color(0xFF172B43),
                secondary = Color(0xFF536170),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFE3E8EE),
                onSecondaryContainer = Color(0xFF1C2630),
                background = SteelBluePrimitives.LightCanvas,
                onBackground = SteelBluePrimitives.LightTextPrimary,
                surface = SteelBluePrimitives.LightSurface,
                onSurface = SteelBluePrimitives.LightTextPrimary,
                surfaceVariant = SteelBluePrimitives.LightSurfaceRaised,
                onSurfaceVariant = SteelBluePrimitives.LightTextSecondary,
                surfaceDim = SteelBluePrimitives.LightSurfaceRaised,
                surfaceBright = SteelBluePrimitives.LightSurface,
                surfaceContainerLowest = SteelBluePrimitives.LightSurfaceLowest,
                surfaceContainerLow = SteelBluePrimitives.LightSurfaceLow,
                surfaceContainer = SteelBluePrimitives.LightSurfaceContainer,
                surfaceContainerHigh = SteelBluePrimitives.LightSurfaceRaised,
                surfaceContainerHighest = SteelBluePrimitives.LightSurfaceHighest,
                inverseSurface = SteelBluePrimitives.DarkSurface,
                inverseOnSurface = SteelBluePrimitives.DarkTextPrimary,
                inversePrimary = SteelBluePrimitives.DarkAccent,
                outline = SteelBluePrimitives.LightTextTertiary,
                outlineVariant = SteelBluePrimitives.LightDivider,
                surfaceTint = SteelBluePrimitives.LightAccent,
            ),
            extended = AuralisExtendedColors(
                contentTertiary = SteelBluePrimitives.LightTextTertiary,
                contentDisabled = SteelBluePrimitives.LightDisabled,
                glassLiquidTint = Color.White.copy(alpha = 0.18f),
                glassFrostedTint = Color.White.copy(alpha = 0.54f),
            ),
        ),
        dark = AuralisThemeSpec(
            material = darkColorScheme(
                primary = SteelBluePrimitives.DarkAccent,
                onPrimary = Color(0xFF0C1B2B),
                primaryContainer = SteelBluePrimitives.DarkAccentSoft,
                onPrimaryContainer = Color(0xFFD8E9FC),
                secondary = Color(0xFFB8C4D0),
                onSecondary = Color(0xFF24313D),
                secondaryContainer = Color(0xFF303C48),
                onSecondaryContainer = Color(0xFFD7E3EF),
                background = SteelBluePrimitives.DarkCanvas,
                onBackground = SteelBluePrimitives.DarkTextPrimary,
                surface = SteelBluePrimitives.DarkSurface,
                onSurface = SteelBluePrimitives.DarkTextPrimary,
                surfaceVariant = SteelBluePrimitives.DarkSurfaceRaised,
                onSurfaceVariant = SteelBluePrimitives.DarkTextSecondary,
                surfaceDim = SteelBluePrimitives.DarkCanvas,
                surfaceBright = SteelBluePrimitives.DarkSurfaceBright,
                surfaceContainerLowest = SteelBluePrimitives.DarkSurfaceLowest,
                surfaceContainerLow = SteelBluePrimitives.DarkSurfaceLow,
                surfaceContainer = SteelBluePrimitives.DarkSurfaceContainer,
                surfaceContainerHigh = SteelBluePrimitives.DarkSurfaceHigh,
                surfaceContainerHighest = SteelBluePrimitives.DarkSurfaceRaised,
                inverseSurface = SteelBluePrimitives.LightSurface,
                inverseOnSurface = SteelBluePrimitives.LightTextPrimary,
                inversePrimary = SteelBluePrimitives.LightAccent,
                outline = SteelBluePrimitives.DarkTextTertiary,
                outlineVariant = SteelBluePrimitives.DarkDivider,
                surfaceTint = SteelBluePrimitives.DarkAccent,
            ),
            extended = AuralisExtendedColors(
                contentTertiary = SteelBluePrimitives.DarkTextTertiary,
                contentDisabled = SteelBluePrimitives.DarkDisabled,
                glassLiquidTint = SteelBluePrimitives.DarkCanvas.copy(alpha = 0.28f),
                glassFrostedTint = SteelBluePrimitives.DarkCanvas.copy(alpha = 0.58f),
            ),
        ),
    )

    val default: AuralisColorSystem = SteelBlue
    val all: List<AuralisColorSystem> = listOf(SteelBlue)

    fun fromKey(key: String?): AuralisColorSystem =
        all.firstOrNull { it.key == key } ?: default
}
