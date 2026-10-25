package com.bobo.auralis.mobile.ui.demo

import com.bobo.auralis.mobile.ui.app.*

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bobo.auralis.mobile.library.browser.LibrarySnapshot
import com.bobo.auralis.mobile.playback.PlaybackConnectionState
import com.bobo.auralis.mobile.playback.PlaybackMode
import com.bobo.auralis.mobile.ui.theme.AlbumLayoutMode
import com.bobo.auralis.mobile.ui.theme.AlbumLayoutAnimationStyle
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun detailsTabsAndPlayerKeepTheirExistingBackStackBehavior() {
        lateinit var navigation: DemoNavigationState
        lateinit var pages: LibraryPageState
        compose.setContent {
            navigation = rememberDemoNavigationState()
            pages = rememberLibraryPageState(navigation.genreId)
            MaterialTheme {
                DemoNavHost(
                    navigation, pages, LibrarySnapshot(), PlaybackConnectionState(), null, 94.dp,
                    AlbumLayoutMode.Grid2, AlbumLayoutAnimationStyle.Reflow,
                    {}, { _, _ -> }, {}, {}, {}, {}, PlaybackMode.Sequential, {},
                )
            }
        }
        fun route(expected: String) {
            compose.waitForIdle()
            compose.runOnIdle { assertEquals(expected, navigation.currentRoute) }
        }
        route("home")
        compose.runOnIdle { navigation.openPlayer(false) }
        route("home")
        compose.runOnIdle { navigation.selectMainDestination("library") }
        route("library")
        val initialPages = pages
        compose.runOnIdle { navigation.openLibrarySection("library/albums") }
        route("library/albums")
        compose.runOnIdle { navigation.openAlbum("missing-album") }
        route("album/{albumId}")
        compose.runOnIdle {
            assertEquals("library", navigation.selectedDockRoute)
            navigation.back()
        }
        route("library/albums")
        compose.runOnIdle { navigation.selectMainDestination("search") }
        route("search")
        compose.runOnIdle { navigation.selectMainDestination("library") }
        route("library/albums")
        compose.runOnIdle { navigation.openPlayer(true) }
        route("player")
        compose.runOnIdle { navigation.back() }
        route("library/albums")
        compose.runOnIdle {
            assertSame(initialPages.albumsGridState, pages.albumsGridState)
            assertSame(initialPages.tracksListState, pages.tracksListState)
            navigation.openGenre("missing-genre")
        }
        route("library/genres/{genreId}")
        compose.runOnIdle { assertEquals("missing-genre", navigation.genreId) }
        val genreState = pages.genreTracksListState
        compose.runOnIdle { navigation.back() }
        route("library/albums")
        compose.runOnIdle {
            assertNull(navigation.genreId)
            assertNotSame(genreState, pages.genreTracksListState)
            assertSame(initialPages.albumsGridState, pages.albumsGridState)
        }
    }
}
