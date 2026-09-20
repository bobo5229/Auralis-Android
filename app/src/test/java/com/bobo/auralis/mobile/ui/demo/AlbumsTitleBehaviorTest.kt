package com.bobo.auralis.mobile.ui.demo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryContentTitleBehaviorTest {
    @Test
    fun contentTitleKeepsTopBarTitleHidden() {
        assertFalse(shouldShowPinnedLibraryContentTitle(firstVisibleItemIndex = 0))
    }

    @Test
    fun topBarTitleAppearsAfterContentTitleLeavesViewport() {
        assertTrue(shouldShowPinnedLibraryContentTitle(firstVisibleItemIndex = 1))
    }
}
