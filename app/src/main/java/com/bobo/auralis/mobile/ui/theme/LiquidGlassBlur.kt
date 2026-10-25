package com.bobo.auralis.mobile.ui.theme

import kotlin.math.roundToInt

internal object LiquidGlassBlur {
    const val DefaultDp = 1f
    const val MinDp = 0f
    const val MaxDp = 5f
    const val StepDp = 0.5f
    const val SliderSteps = 9

    fun normalize(dp: Float): Float = if (dp.isFinite()) {
        (dp.coerceIn(MinDp, MaxDp) / StepDp).roundToInt() * StepDp
    } else DefaultDp
}
