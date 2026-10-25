package com.bobo.auralis.mobile.ui.demo

import com.bobo.auralis.mobile.ui.theme.GlassStyle

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

internal fun Modifier.auralisGlass(
    backdrop: Backdrop,
    shape: Shape,
    surfaceColor: Color,
    glassStyle: GlassStyle,
    liquidGlassBlurDp: Float,
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        when (glassStyle) {
            GlassStyle.Liquid -> {
                vibrancy()
                blur(liquidGlassBlurDp.dp.toPx())
                lens(
                    refractionHeight = size.minDimension * 0.28f,
                    refractionAmount = size.minDimension * 0.58f,
                    depthEffect = true,
                    chromaticAberration = true,
                )
            }
            GlassStyle.Frosted -> {
                vibrancy()
                blur(7.dp.toPx())
                lens(12.dp.toPx(), 20.dp.toPx())
            }
        }
    },
    onDrawSurface = { drawRect(surfaceColor) },
)
