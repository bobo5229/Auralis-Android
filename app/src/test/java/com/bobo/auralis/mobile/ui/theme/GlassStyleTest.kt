package com.bobo.auralis.mobile.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class GlassStyleTest {
    @Test fun persistedKeysRoundTrip() {
        assertEquals(GlassStyle.Liquid, GlassStyle.fromKey("liquid"))
        assertEquals(GlassStyle.Frosted, GlassStyle.fromKey("frosted"))
        GlassStyle.entries.forEach { assertEquals(it, GlassStyle.fromKey(it.key)) }
    }

    @Test fun missingAndUnknownPreferencesDefaultToLiquid() {
        assertEquals(GlassStyle.Liquid, GlassStyle.fromKey(null))
        assertEquals(GlassStyle.Liquid, GlassStyle.fromKey(""))
        assertEquals(GlassStyle.Liquid, GlassStyle.fromKey("unknown"))
    }
}
