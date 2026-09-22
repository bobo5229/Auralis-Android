package com.bobo.auralis.mobile.ui.demo

import com.bobo.auralis.mobile.thirdparty.materialcolorutilities.hct.Hct
import org.junit.Assert.*
import org.junit.Test

class ArtworkColorAlgorithmTest {
    @Test
    fun emptyAndTransparentImagesHaveNoSeed() {
        assertNull(extractArtworkSeedColor(intArrayOf()))
        assertNull(extractArtworkSeedColor(intArrayOf(0x00FF0000, 0x800000FF.toInt())))
    }

    @Test
    fun solidWarmCoverKeepsItsHue() {
        val source = 0xFFC47748.toInt()
        val seed = requireNotNull(extractArtworkSeedColor(IntArray(400) { source }))
        assertTrue(hueDistance(Hct.fromInt(source).hue, Hct.fromInt(seed).hue) < 3.0)
    }

    @Test
    fun blackWhiteAndGrayCoversRemainNeutralInBothThemes() {
        for (source in listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF888888.toInt())) {
            val seed = requireNotNull(extractArtworkSeedColor(IntArray(100) { source }))
            for (dark in listOf(false, true)) {
                val color = adjustArtworkTrackColor(seed, dark)
                val channels = listOf((color ushr 16) and 255, (color ushr 8) and 255, color and 255)
                assertTrue(channels.max() - channels.min() <= 1)
            }
        }
    }

    @Test
    fun accentCanBeSelectedOverNeutralBackgroundAndIgnoresTransparentPadding() {
        val red = 0xFFE53935.toInt()
        val pixels = IntArray(1000) { if (it < 800) 0xFF888888.toInt() else red }
        val seed = requireNotNull(extractArtworkSeedColor(pixels))
        assertTrue(hueDistance(Hct.fromInt(red).hue, Hct.fromInt(seed).hue) < 5.0)
        assertEquals(seed, extractArtworkSeedColor(pixels + IntArray(1000) { 0x000000FF }))
        assertEquals(seed, extractArtworkSeedColor(pixels))
    }

    @Test
    fun hctAdjustmentKeepsHueAndSetsThemeTone() {
        for (source in listOf(0xFF185EA8.toInt(), 0xFFC47748.toInt(), 0xFF30A060.toInt())) {
            val original = Hct.fromInt(source)
            for (dark in listOf(false, true)) {
                val adjusted = Hct.fromInt(adjustArtworkTrackColor(source, dark))
                assertTrue(hueDistance(original.hue, adjusted.hue) < 5.0)
                assertTrue(adjusted.chroma <= original.chroma + 2.0)
                assertTrue(adjusted.tone in if (dark) 69.5..80.5 else 34.5..45.5)
            }
        }
    }

    private fun hueDistance(a: Double, b: Double): Double {
        val distance = kotlin.math.abs(a - b)
        return minOf(distance, 360.0 - distance)
    }
}
