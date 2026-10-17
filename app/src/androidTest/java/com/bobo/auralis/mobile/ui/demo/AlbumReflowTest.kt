package com.bobo.auralis.mobile.ui.demo

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bobo.auralis.mobile.library.browser.LibraryAlbum
import com.bobo.auralis.mobile.library.model.AlbumId
import com.bobo.auralis.mobile.ui.theme.AlbumLayoutAnimationStyle
import com.bobo.auralis.mobile.ui.theme.AlbumLayoutMode
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlbumReflowTest {
    @get:Rule val compose = createComposeRule()

    private val mode = mutableStateOf(AlbumLayoutMode.Grid2)
    private val album = LibraryAlbum(
        albumId = AlbumId.random(),
        title = "Animation probe",
        albumArtists = listOf("Artist"),
        date = null,
        tracks = emptyList(),
        artworkSource = null,
    )

    private fun show(initialMode: AlbumLayoutMode) {
        mode.value = initialMode
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(360.dp).height(640.dp)) {
                    DemoAlbumsScreen(
                        albums = listOf(album),
                        bottomPadding = 0.dp,
                        gridState = rememberLazyGridState(),
                        layoutMode = mode.value,
                        animationStyle = AlbumLayoutAnimationStyle.Reflow,
                        onLayoutModeSelected = { mode.value = it },
                        onAlbumClick = {},
                    )
                }
            }
        }
    }

    private fun changeTo(target: AlbumLayoutMode, elapsedMs: Long) {
        compose.runOnIdle { mode.value = target }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        // Android's measure pass supplies the new grid constraints separately
        // from the Compose animation clock. Flush it before sampling elapsed time.
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(elapsedMs)
    }

    private fun artworkWidth(): Float {
        // Inspect rendered pixels: the cover now scales in its layer while
        // measured dimensions stay fixed throughout the transition.
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var widest = 0
        repeat(bitmap.height) { y ->
            var left = bitmap.width
            var right = -1
            repeat(bitmap.width) { x ->
                val color = pixels[y * bitmap.width + x]
                val red = (color shr 16) and 255
                val green = (color shr 8) and 255
                val blue = color and 255
                if (maxOf(red, green, blue) - minOf(red, green, blue) >= 40) {
                    left = minOf(left, x)
                    right = maxOf(right, x)
                }
            }
            widest = maxOf(widest, right - left + 1)
        }
        return widest.toFloat()
    }

    private fun capture(name: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "reflow-validation")
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use { output ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    @Test
    fun pinchStartsFromTheLatestModeAfterAnExternalSelection() {
        show(AlbumLayoutMode.Grid2)
        // Start the pointer coroutine, then leave it waiting while a menu-like
        // external selection changes the density.
        compose.onRoot().performTouchInput {
            down(center)
            cancel()
        }
        changeTo(AlbumLayoutMode.Grid3, 2_000)
        compose.onRoot().performTouchInput {
            pinch(
                start0 = Offset(width * 0.30f, height * 0.45f),
                end0 = Offset(width * 0.25f, height * 0.45f),
                start1 = Offset(width * 0.70f, height * 0.45f),
                end1 = Offset(width * 0.75f, height * 0.45f),
                durationMillis = 300,
            )
        }
        compose.runOnIdle { org.junit.Assert.assertEquals(AlbumLayoutMode.Grid2, mode.value) }
    }

    @Test
    fun increasingColumnsKeepsVisibleIntermediateWidthInsteadOfClampingToNewSlot() {
        show(AlbumLayoutMode.Grid2)
        val initial = artworkWidth()
        capture("shrink-start")
        changeTo(AlbumLayoutMode.Grid3, 80)
        val intermediate = artworkWidth()
        capture("shrink-middle")
        compose.mainClock.advanceTimeBy(2_000)
        val final = artworkWidth()
        capture("shrink-end")

        assertTrue("Shrink should expose an intermediate width: $initial -> $intermediate -> $final", intermediate > final + 1f && intermediate < initial - 1f)
    }

    @Test
    fun decreasingColumnsUsesTheSameContinuousWidthTransition() {
        show(AlbumLayoutMode.Grid3)
        val initial = artworkWidth()
        capture("grow-start")
        changeTo(AlbumLayoutMode.Grid2, 80)
        val intermediate = artworkWidth()
        capture("grow-middle")
        compose.mainClock.advanceTimeBy(2_000)
        val final = artworkWidth()
        capture("grow-end")

        assertTrue("Grow should expose an intermediate width: $initial -> $intermediate -> $final", intermediate > initial + 1f && intermediate < final - 1f)
    }

    @Test
    fun reversingDuringShrinkSettlesAtTheLatestGridBounds() {
        show(AlbumLayoutMode.Grid2)
        val original = artworkWidth()
        changeTo(AlbumLayoutMode.Grid3, 80)
        val interrupted = artworkWidth()
        changeTo(AlbumLayoutMode.Grid2, 80)
        val returning = artworkWidth()
        compose.mainClock.advanceTimeBy(240)
        val approaching = artworkWidth()
        compose.mainClock.advanceTimeBy(2_000)
        val final = artworkWidth()
        capture("reversed-end")

        // Springs preserve velocity, so a reversal may initially continue shrinking.
        assertTrue("Reverse should start from the interrupted width", kotlin.math.abs(returning - interrupted) < kotlin.math.abs(original - interrupted))
        assertTrue("Reverse should then approach the latest target", approaching > returning)
        assertTrue("Interrupted transition should settle at the latest bounds", kotlin.math.abs(final - original) <= 1f)
    }

    @Test
    fun titlePlacementTracksTheAnimatedCoverWithoutJumpingToTheTarget() {
        show(AlbumLayoutMode.Grid2)
        fun titleTop() = compose.onNodeWithText(album.title, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.top
        val coverInitial = artworkWidth() // Flush Android's layer/placement pass before reading coordinates.
        val initial = titleTop()
        changeTo(AlbumLayoutMode.Grid3, 80)
        val coverIntermediate = artworkWidth()
        val intermediate = titleTop()
        compose.mainClock.advanceTimeBy(2_000)
        val coverFinal = artworkWidth()
        val final = titleTop()

        assertTrue("Title should move through an intermediate position", intermediate > final + 1f && intermediate < initial - 1f)
        // Generated artwork has rounded/low-saturation edges, so its colored
        // pixel span differs from the full square. Compare normalized progress.
        val titleProgress = (intermediate - final) / (initial - final)
        val coverProgress = (coverIntermediate - coverFinal) / (coverInitial - coverFinal)
        assertTrue("Title and artwork should progress together: $titleProgress vs $coverProgress", kotlin.math.abs(titleProgress - coverProgress) <= 0.05f)
    }

    @Test
    fun listAndCoverlessTransitionsRestoreTheGridArtwork() {
        show(AlbumLayoutMode.Grid2)
        val original = artworkWidth()
        changeTo(AlbumLayoutMode.List, 2_000)
        val listWidth = artworkWidth()
        assertTrue("List artwork should shrink to its row size", listWidth in 1f..original - 1f)
        changeTo(AlbumLayoutMode.Coverless, 2_000)
        assertTrue("Coverless layout must hide the artwork", artworkWidth() == 0f)
        changeTo(AlbumLayoutMode.Grid2, 2_000)
        assertTrue("Grid must restore its artwork", kotlin.math.abs(artworkWidth() - original) <= 1f)
    }
}
