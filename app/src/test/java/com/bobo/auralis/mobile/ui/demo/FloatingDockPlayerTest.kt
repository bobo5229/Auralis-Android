package com.bobo.auralis.mobile.ui.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingDockPlayerTest {
    @Test
    fun downwardDragUsesCappedResistance() {
        assertEquals(0f, dampedDragOffsetPx(0f, 6f, 2.5f), 0f)
        assertEquals(3f, dampedDragOffsetPx(6f, 6f, 2.5f), 0.0001f)
        assertTrue(dampedDragOffsetPx(600f, 6f, 2.5f) < 6f)
    }

    @Test
    fun upwardDragUsesStrongerRubberBandResistance() {
        val upward = dampedDragOffsetPx(-60f, 6f, 2.5f)
        val downward = dampedDragOffsetPx(60f, 6f, 2.5f)

        assertTrue(upward < 0f)
        assertTrue(kotlin.math.abs(upward) < 2.5f)
        assertTrue(kotlin.math.abs(upward) < downward)
    }

    @Test
    fun navigationDropTargetUsesFingerPosition() {
        assertTrue(isPointInsideBounds(50f, 90f, 0f, 80f, 100f, 120f))
        assertFalse(isPointInsideBounds(50f, 79.9f, 0f, 80f, 100f, 120f))
        assertFalse(isPointInsideBounds(101f, 90f, 0f, 80f, 100f, 120f))
    }

    @Test
    fun playbackProgressClampsAndHandlesUnknownDuration() {
        assertEquals(0f, playbackProgress(500L, null), 0f)
        assertEquals(0f, playbackProgress(500L, 0L), 0f)
        assertEquals(0f, playbackProgress(-1L, 1_000L), 0f)
        assertEquals(0.5f, playbackProgress(500L, 1_000L), 0f)
        assertEquals(1f, playbackProgress(2_000L, 1_000L), 0f)
    }

    @Test
    fun waveShapeIsStableForIdentityAndChangesWithSeed() {
        val first = List(193) { waveOutwardOffsetFactor(42, it, 192) }
        val repeated = List(193) { waveOutwardOffsetFactor(42, it, 192) }
        val different = List(193) { waveOutwardOffsetFactor(43, it, 192) }

        assertEquals(first, repeated)
        assertNotEquals(first, different)
        assertEquals(first.first(), first.last(), 0.000001f)
        assertTrue(first.all { it in 0f..1f })
        val localPeaks = first.windowed(3).count { (left, center, right) -> center > left && center > right }
        assertTrue(localPeaks >= 16)
    }

    @Test
    fun waveAddsOneOrTwoStableAccentPeaks() {
        assertEquals(1, waveAccentPeakCount(42))
        assertEquals(2, waveAccentPeakCount(43))

        val first = List(193) { waveAccentFactor(43, it, 192) }
        val repeated = List(193) { waveAccentFactor(43, it, 192) }

        assertEquals(first, repeated)
        assertEquals(first.first(), first.last(), 0.000001f)
        assertTrue(first.all { it in 0f..1f })
        assertTrue(first.max() > 0.95f)
    }

    @Test
    fun roundedWaveTrackStartsAboveCoverAndClosesContinuously() {
        val points = List(193) { roundedWaveTrackPoint(42, it, 192, 46f) }
        val first = points.first()
        val last = points.last()

        assertTrue(first.y < 4f)
        assertEquals(first.x, last.x, 0.0001f)
        assertEquals(first.y, last.y, 0.0001f)
        assertTrue(points.all { it.x in 0f..46f && it.y in 0f..46f })
    }

    @Test
    fun navigationCentersExpandFromFourToFiveSlots() {
        assertEquals(listOf(50f, 150f, 250f, 350f), navigationSlotCenters(4, 400f))
        assertEquals(listOf(40f, 120f, 200f, 280f, 360f), navigationSlotCenters(5, 400f))
        assertTrue(navigationSlotCenters(0, 400f).isEmpty())
    }

    @Test
    fun artworkTrackColorIsAdjustedForThemeContrast() {
        val source = 0xFF101010.toInt()
        val darkTheme = adjustArtworkTrackColor(source, isDark = true)
        val lightTheme = adjustArtworkTrackColor(source, isDark = false)

        assertNotEquals(source, darkTheme)
        assertNotEquals(darkTheme, lightTheme)
    }
}
