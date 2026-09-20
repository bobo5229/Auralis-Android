package com.bobo.auralis.mobile.ui.demo

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumLayoutGestureTest {
    @Test
    fun inwardPinchIncreasesDensityAndOutwardPinchDecreasesIt() {
        assertEquals(1, albumLayoutDensitySteps(0.84f))
        assertEquals(2, albumLayoutDensitySteps(0.70f))
        assertEquals(-1, albumLayoutDensitySteps(1.20f))
        assertEquals(-2, albumLayoutDensitySteps(1.40f))
    }

    @Test
    fun smallOrInvalidScaleDoesNotChangeLayout() {
        assertEquals(0, albumLayoutDensitySteps(0.95f))
        assertEquals(0, albumLayoutDensitySteps(1.05f))
        assertEquals(0, albumLayoutDensitySteps(Float.NaN))
        assertEquals(0, albumLayoutDensitySteps(0f))
    }
}
