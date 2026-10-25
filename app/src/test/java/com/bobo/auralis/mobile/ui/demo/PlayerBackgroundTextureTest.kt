package com.bobo.auralis.mobile.ui.demo

import org.junit.Assert.*
import org.junit.Test

class PlayerBackgroundTextureTest {
    private fun red(pixel: Int) = (pixel ushr 16) and 255
    private fun green(pixel: Int) = (pixel ushr 8) and 255
    private fun blue(pixel: Int) = pixel and 255

    @Test fun monochromeStaysNeutralAndHighlightsHaveHeadroom() {
        for (level in listOf(0, 32, 128, 255)) {
            val input = (0xff shl 24) or (level shl 16) or (level shl 8) or level
            val output = prepareBackgroundPixels(IntArray(32 * 32) { input }, 32, 32)
            assertTrue(output.all { red(it) == green(it) && green(it) == blue(it) })
            assertTrue(output.all { red(it) in 1..170 })
            assertEquals(1, output.distinct().size)
        }
    }

    @Test fun blurSoftensBoundaryWithoutAveragingAwayColorRegions() {
        val input = IntArray(32 * 32) { if (it % 32 < 16) 0xffd02010.toInt() else 0xff1020d0.toInt() }
        val copy = input.copyOf()
        val result = prepareBackgroundPixels(input, 32, 32)
        assertArrayEquals(copy, input)
        assertTrue(red(result[16 * 32 + 3]) > blue(result[16 * 32 + 3]) + 50)
        assertTrue(blue(result[16 * 32 + 28]) > red(result[16 * 32 + 28]) + 50)
        val transition = (10..21).map { red(result[16 * 32 + it]) }
        assertTrue(transition.distinct().size >= 8)
        assertTrue(transition.zipWithNext().all { (a, b) -> a >= b })
    }

    @Test fun transparentColorsDoNotLeakAndSinglePixelIsSupported() {
        assertArrayEquals(
            prepareBackgroundPixels(intArrayOf(0x00ffffff), 1, 1),
            prepareBackgroundPixels(intArrayOf(0x00000000), 1, 1),
        )
        val red = prepareBackgroundPixels(intArrayOf(0xffa03020.toInt()), 1, 1)[0]
        assertTrue(red(red) > green(red) && green(red) > blue(red))
        assertEquals(255, red ushr 24)
    }
}
