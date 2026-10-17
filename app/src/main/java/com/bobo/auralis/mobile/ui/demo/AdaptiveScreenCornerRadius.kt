package com.bobo.auralis.mobile.ui.demo

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

@Composable
internal fun rememberAdaptiveScreenCornerRadius(
    horizontalInset: androidx.compose.ui.unit.Dp,
    minimumRadius: androidx.compose.ui.unit.Dp,
    maximumRadius: androidx.compose.ui.unit.Dp,
    fallbackRadius: androidx.compose.ui.unit.Dp,
) = with(LocalDensity.current) {
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    var screenCornerRadiusPx by remember(view) { mutableStateOf<Int?>(null) }

    LaunchedEffect(
        view,
        configuration.orientation,
        configuration.screenWidthDp,
        configuration.screenHeightDp,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            screenCornerRadiusPx = null
            return@LaunchedEffect
        }

        repeat(3) {
            withFrameNanos { }
            readBottomScreenCornerRadiusPx(view.rootWindowInsets)?.let { radius ->
                screenCornerRadiusPx = radius
                return@LaunchedEffect
            }
        }
        screenCornerRadiusPx = null
    }

    calculateDockCornerRadiusDp(
        screenCornerRadiusDp = screenCornerRadiusPx?.toDp()?.value,
        horizontalInsetDp = horizontalInset.value,
        minimumRadiusDp = minimumRadius.value,
        maximumRadiusDp = maximumRadius.value,
        fallbackRadiusDp = fallbackRadius.value,
    ).dp
}

private fun readBottomScreenCornerRadiusPx(windowInsets: android.view.WindowInsets?): Int? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || windowInsets == null) return null
    val radii = listOfNotNull(
        windowInsets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius,
        windowInsets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius,
    ).filter { it > 0 }
    return radii.minOrNull()
}

internal fun calculateDockCornerRadiusDp(
    screenCornerRadiusDp: Float?,
    horizontalInsetDp: Float = 12f,
    minimumRadiusDp: Float = 20f,
    maximumRadiusDp: Float = 35f,
    fallbackRadiusDp: Float = 27f,
): Float {
    if (screenCornerRadiusDp == null || screenCornerRadiusDp <= 0f) return fallbackRadiusDp
    return (screenCornerRadiusDp - horizontalInsetDp).coerceIn(minimumRadiusDp, maximumRadiusDp)
}
