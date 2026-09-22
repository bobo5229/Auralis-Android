package com.bobo.auralis.mobile.ui.demo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollapsedPlayerGestureTest {
    @Test
    fun upwardSwipeAtThresholdExpands() {
        assertTrue(shouldExpandCollapsedPlayer(0f, -24f, 24f))
        assertTrue(shouldExpandCollapsedPlayer(10f, -30f, 24f))
    }

    @Test
    fun stationaryShortOrDownwardDragDoesNotExpand() {
        assertFalse(shouldExpandCollapsedPlayer(0f, 0f, 24f))
        assertFalse(shouldExpandCollapsedPlayer(0f, -23f, 24f))
        assertFalse(shouldExpandCollapsedPlayer(0f, 40f, 24f))
    }

    @Test
    fun predominantlyHorizontalSwipeDoesNotExpand() {
        assertFalse(shouldExpandCollapsedPlayer(40f, -30f, 24f))
        assertFalse(shouldExpandCollapsedPlayer(-40f, -30f, 24f))
        assertFalse(shouldExpandCollapsedPlayer(30f, -30f, 24f))
    }
}
