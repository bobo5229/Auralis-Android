package com.bobo.auralis.mobile.ui.demo

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

internal enum class DemoGlassStyle(val label: String) {
    Liquid("Liquid Glass"),
    Frosted("磨砂玻璃"),
}

internal fun Modifier.auralisGlass(
    backdrop: Backdrop,
    shape: Shape,
    surfaceColor: Color,
    glassStyle: DemoGlassStyle,
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        when (glassStyle) {
            DemoGlassStyle.Liquid -> {
                vibrancy()
                blur(2.dp.toPx())
                lens(
                    refractionHeight = size.minDimension * 0.28f,
                    refractionAmount = size.minDimension * 0.58f,
                    depthEffect = true,
                    chromaticAberration = true,
                )
            }
            DemoGlassStyle.Frosted -> {
                vibrancy()
                blur(7.dp.toPx())
                lens(12.dp.toPx(), 20.dp.toPx())
            }
        }
    },
    onDrawSurface = { drawRect(surfaceColor) },
)
