package com.bobo.auralis.mobile.ui.demo

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.bobo.auralis.mobile.library.browser.LibraryTrack
import com.bobo.auralis.mobile.library.model.TrackId
import com.bobo.auralis.mobile.playback.PlaybackMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlayerLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val track = LibraryTrack(TrackId(java.util.UUID(0, 1)), null, "共同渡過", listOf("张国荣"),
        "Leslie Four Seasons", 265000, 1, 1, true, null, null)

    private fun verify(compact: Boolean) {
        var toggles = 0
        var modes = 0
        var seek = 0L
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (compact) 1.3f else 1f)) {
                MaterialTheme {
                    Box(if (compact) Modifier.width(320.dp).height(600.dp) else Modifier.fillMaxSize()) {
                        DemoPlayerScreen(null, track, true, 2000, 265000, {}, { toggles++ }, {}, {},
                            { seek = it }, PlaybackMode.Sequential, { modes++ })
                    }
                }
            }
        }
        compose.onNodeWithText("共同渡過").assertIsDisplayed()
        compose.onNodeWithText("−4:23").assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.cacheDir, if (compact) "player-compact.png" else "player-normal.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithContentDescription("暂停").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("播放模式：顺序播放").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("播放进度").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0.5f) }
        compose.runOnIdle { assertEquals(1, toggles); assertEquals(1, modes); assertEquals(132500L, seek) }
        compose.onNodeWithContentDescription("显示歌词").assertIsDisplayed().performClick()
        compose.onNodeWithText("暂无歌词").assertIsDisplayed()
        compose.onNodeWithContentDescription("显示封面").performClick()
        compose.onNodeWithContentDescription("显示歌词").assertIsDisplayed()
    }
    @Test fun normalPhoneLayoutAndControls() = verify(false)
    @Test fun compactLayoutWithLargeTextKeepsControlsVisible() = verify(true)
}
