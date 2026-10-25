package com.bobo.auralis.mobile.ui.demo

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class PlayerBackgroundTest {
    @get:Rule val compose = createComposeRule()

    private fun prepareTexture(bitmap: Bitmap): Bitmap {
        val small = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
        val pixels = IntArray(32 * 32)
        small.getPixels(pixels, 0, 32, 0, 0, 32, 32)
        return Bitmap.createBitmap(prepareBackgroundPixels(pixels, 32, 32), 32, 32, Bitmap.Config.ARGB_8888)
    }

    @Test fun shaderPreservesColorVariationAndKeepsWhiteCoverDark() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val artwork = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) for (x in 0 until width) {
                setPixel(x, y, Color.rgb(30 + x, 20 + y / 2, 15))
            }
        }
        val current = mutableStateOf(prepareTexture(artwork))
        compose.setContent { FlowingArtwork(current.value, false, Modifier.fillMaxSize()) }
        val rendered = compose.onRoot().captureToImage().asAndroidBitmap()
        val colors = (1..8).map { rendered.getPixel(rendered.width / 2, rendered.height * it / 10) }
        assertTrue("Artwork should retain spatial color variation", colors.distinct().size > 3)
        assertTrue("Red artwork should remain red", colors.all { Color.red(it) > Color.blue(it) })
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, "player-background.png").outputStream().use {
            rendered.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.runOnIdle {
            current.value = prepareTexture(Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) })
        }
        val white = compose.onRoot().captureToImage().asAndroidBitmap()
        for (y in listOf(1, white.height / 2, white.height - 2)) {
            assertTrue("White cover must not wash out controls", Color.red(white.getPixel(white.width / 2, y)) <= 151)
        }
    }
}
