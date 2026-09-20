package com.bobo.auralis.mobile.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AuralisColorSystemsTest {
    @Test
    fun `registered color system keys are unique`() {
        val keys = AuralisColorSystems.all.map { it.key }

        assertEquals(keys.distinct(), keys)
    }

    @Test
    fun `known key resolves registered color system`() {
        assertSame(
            AuralisColorSystems.SteelBlue,
            AuralisColorSystems.fromKey("steel_blue"),
        )
    }

    @Test
    fun `unknown or missing key falls back to default`() {
        assertSame(AuralisColorSystems.default, AuralisColorSystems.fromKey("not_registered"))
        assertSame(AuralisColorSystems.default, AuralisColorSystems.fromKey(null))
    }
}
