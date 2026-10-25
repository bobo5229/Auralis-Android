package com.bobo.auralis.mobile.ui.demo

import com.bobo.auralis.mobile.ui.app.*

import org.junit.Assert.*
import org.junit.Test

class DemoRoutesTest {
    @Test fun mainDestinationsSelectTheirOwnDockItem() {
        listOf("home", "library", "roam", "search").forEach {
            assertTrue(isMainRoute(it))
            assertEquals(it, dockRouteFor(it))
        }
    }

    @Test fun libraryDetailsKeepLibrarySelected() {
        listOf("library/albums", "library/tracks", "library/playlists", "library/genres",
            "library/genres/{genreId}", "album/{albumId}", "album/123").forEach {
            assertFalse(isMainRoute(it))
            assertEquals("library", dockRouteFor(it))
        }
    }

    @Test fun playerAndUnknownRoutesRetainHomeFallback() {
        listOf("player", "unknown", "album", "library-other").forEach {
            assertFalse(isMainRoute(it))
            assertEquals("home", dockRouteFor(it))
        }
    }
}
