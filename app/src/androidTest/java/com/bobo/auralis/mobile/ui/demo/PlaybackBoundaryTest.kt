package com.bobo.auralis.mobile.ui.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.bobo.auralis.mobile.library.browser.LibrarySnapshot
import com.bobo.auralis.mobile.library.model.TrackId
import com.bobo.auralis.mobile.playback.PlaybackConnectionState
import com.bobo.auralis.mobile.playback.PlaybackMode
import com.bobo.auralis.mobile.playback.PlaybackTrack
import com.bobo.auralis.mobile.ui.app.DemoProductShell
import com.bobo.auralis.mobile.ui.app.toPlayerTrack
import com.bobo.auralis.mobile.ui.app.mainDestinations
import com.bobo.auralis.mobile.ui.theme.GlassStyle
import com.bobo.auralis.mobile.ui.theme.AlbumLayoutAnimationStyle
import com.bobo.auralis.mobile.ui.theme.AlbumLayoutMode
import com.bobo.auralis.mobile.ui.theme.AuralisThemeMode
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlaybackBoundaryTest {
    @get:Rule val compose = createComposeRule()
    private val queued = PlaybackTrack(TrackId.random(), "Queued song", listOf("Queued artist"),
        null, "Queued album", 180_000, 2, 3, null, false)

    @Test fun currentItemControlsRemainAvailableAfterVisibleLibraryBecomesEmpty() {
        val library = mutableStateOf(LibrarySnapshot(tracks = listOf(queued.toPlayerTrack().copy(title = "Changed library title"))))
        var toggles = 0
        var next = 0
        compose.setContent {
            MaterialTheme {
                DemoProductShell(
                    library = library.value,
                    playback = PlaybackConnectionState(currentMediaId = queued.trackId.toString(), currentTrack = queued, showPauseButton = true),
                    onPlay = { _, _ -> }, onTogglePlayback = { toggles++ }, onNext = { next++ },
                    onPrevious = {}, onSeek = {}, playbackMode = PlaybackMode.Sequential,
                    onPlaybackModeSelected = {}, glassStyle = GlassStyle.Frosted, onGlassStyleSelected = {},
                    liquidGlassBlurDp = 1f, onLiquidGlassBlurDpChanged = {},
                    themeMode = AuralisThemeMode.System, onThemeModeSelected = {},
                    albumLayoutMode = AlbumLayoutMode.Grid2, onAlbumLayoutModeSelected = {},
                    albumLayoutAnimationStyle = AlbumLayoutAnimationStyle.Fade, onAlbumLayoutAnimationStyleSelected = {},
                )
            }
        }
        compose.onNodeWithText("Queued song").assertIsDisplayed()
        compose.runOnIdle { library.value = LibrarySnapshot() }
        compose.onNodeWithText("Queued song").assertIsDisplayed()
        compose.onNodeWithContentDescription("暂停").performClick()
        compose.onNodeWithText("Queued song").performClick()
        compose.onNodeWithText("Queued artist").assertIsDisplayed()
        compose.onNodeWithContentDescription("下一首").performClick()
        compose.runOnIdle { assertEquals(1, toggles); assertEquals(1, next) }
    }

    @Test fun leavingDockDuringReturnAnimationAlwaysEndsParentInteraction() {
        val visible = mutableStateOf(true)
        var interacting = false
        compose.setContent {
            MaterialTheme {
                val backdrop = rememberLayerBackdrop { drawContent() }
                Box(Modifier.fillMaxSize()) {
                    if (visible.value) DemoFloatingDock(
                        backdrop = backdrop, glassStyle = GlassStyle.Frosted, liquidGlassBlurDp = 1f,
                        destinations = mainDestinations, selectedRoute = "home", currentTrack = queued.toPlayerTrack(),
                        showPauseButton = false, positionMs = 0, durationMs = queued.durationMs,
                        isPlayerCollapsed = false, onDestinationSelected = {}, onTogglePlayback = {},
                        onPrevious = {}, onNext = {}, onOpenPlayer = {}, onCollapsePlayer = {}, onExpandPlayer = {},
                        onPlayerInteractionActiveChanged = { interacting = it },
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Queued song").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, 20f))
            advanceEventTime(16)
            up()
        }
        compose.runOnIdle {
            assertTrue("Return animation should still own the interaction", interacting)
            visible.value = false
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.onNodeWithText("Queued song").assertDoesNotExist()
        compose.runOnIdle { assertFalse(interacting) }
        compose.mainClock.autoAdvance = true
    }
}
