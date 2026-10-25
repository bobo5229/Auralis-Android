package com.bobo.auralis.mobile.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class LiquidGlassBlurTest {
    @Test fun supportedValuesRoundTripIncludingZeroAndDefault() {
        assertEquals(1f, LiquidGlassBlur.DefaultDp, 0f)
        for (step in 0..10) {
            val dp = step * 0.5f
            assertEquals(dp, LiquidGlassBlur.normalize(dp), 0f)
        }
    }

    @Test fun valuesAreClampedAndSnappedToHalfDp() {
        assertEquals(0f, LiquidGlassBlur.normalize(-1f), 0f)
        assertEquals(5f, LiquidGlassBlur.normalize(6f), 0f)
        assertEquals(1f, LiquidGlassBlur.normalize(1.24f), 0f)
        assertEquals(1.5f, LiquidGlassBlur.normalize(1.26f), 0f)
    }

    @Test fun invalidNumbersUseDefault() {
        for (dp in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(1f, LiquidGlassBlur.normalize(dp), 0f)
        }
    }
}
