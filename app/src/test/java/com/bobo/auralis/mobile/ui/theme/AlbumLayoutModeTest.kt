package com.bobo.auralis.mobile.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumLayoutModeTest {
    @Test
    fun missingOrUnknownPreferenceFallsBackToTwoColumnGrid() {
        assertEquals(AlbumLayoutMode.Grid2, AlbumLayoutMode.fromKey(null))
        assertEquals(AlbumLayoutMode.Grid2, AlbumLayoutMode.fromKey("unknown"))
    }

    @Test
    fun allPersistedKeysRoundTrip() {
        AlbumLayoutMode.entries.forEach { mode ->
            assertEquals(mode, AlbumLayoutMode.fromKey(mode.key))
        }
    }

    @Test
    fun gridModesAreLimitedToFiveColumns() {
        val gridModes = AlbumLayoutMode.entries.filter(AlbumLayoutMode::isGrid)

        assertEquals(listOf(1, 2, 3, 4, 5), gridModes.map(AlbumLayoutMode::gridColumns))
        assertTrue(AlbumLayoutMode.Grid5.isGrid)
        assertFalse(AlbumLayoutMode.List.isGrid)
        assertFalse(AlbumLayoutMode.Coverless.isGrid)
    }

    @Test
    fun densityShiftTraversesEveryLayoutAndStopsAtBothEnds() {
        assertEquals(AlbumLayoutMode.Grid2, AlbumLayoutMode.Grid1.shiftDensity(1))
        assertEquals(AlbumLayoutMode.List, AlbumLayoutMode.Grid5.shiftDensity(1))
        assertEquals(AlbumLayoutMode.Coverless, AlbumLayoutMode.List.shiftDensity(1))
        assertEquals(AlbumLayoutMode.Coverless, AlbumLayoutMode.Grid1.shiftDensity(6))
        assertEquals(AlbumLayoutMode.Grid1, AlbumLayoutMode.Coverless.shiftDensity(-6))
        assertEquals(AlbumLayoutMode.Coverless, AlbumLayoutMode.Coverless.shiftDensity(20))
        assertEquals(AlbumLayoutMode.Grid1, AlbumLayoutMode.Grid1.shiftDensity(-20))
    }

    @Test
    fun albumLayoutAnimationDefaultsToFadeAndPersistsBothStyles() {
        assertEquals(AlbumLayoutAnimationStyle.Fade, AlbumLayoutAnimationStyle.fromKey(null))
        assertEquals(AlbumLayoutAnimationStyle.Fade, AlbumLayoutAnimationStyle.fromKey("unknown"))
        AlbumLayoutAnimationStyle.entries.forEach { style ->
            assertEquals(style, AlbumLayoutAnimationStyle.fromKey(style.key))
        }
    }
}
