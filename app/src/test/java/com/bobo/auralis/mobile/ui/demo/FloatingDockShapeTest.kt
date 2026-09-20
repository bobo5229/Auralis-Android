package com.bobo.auralis.mobile.ui.demo

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingDockShapeTest {
    @Test
    fun screenCornerUsesConcentricInsetRadius() {
        assertEquals(28f, calculateDockCornerRadiusDp(screenCornerRadiusDp = 40f), 0f)
    }

    @Test
    fun radiusIsLimitedByDockHeight() {
        assertEquals(
            28f,
            calculateDockCornerRadiusDp(screenCornerRadiusDp = 50f, maximumRadiusDp = 28f),
            0f,
        )
    }

    @Test
    fun smallScreenCornerKeepsDockUsable() {
        assertEquals(20f, calculateDockCornerRadiusDp(screenCornerRadiusDp = 18f), 0f)
    }

    @Test
    fun unavailableScreenCornerUsesDesignFallback() {
        assertEquals(27f, calculateDockCornerRadiusDp(screenCornerRadiusDp = null), 0f)
    }
}
