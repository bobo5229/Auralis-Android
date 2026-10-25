package com.bobo.auralis.mobile.ui.demo

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

/** Small artwork-only CPU pass. Input is copied; no Android objects or full-screen work. */
internal fun prepareBackgroundPixels(pixels: IntArray, width: Int, height: Int): IntArray {
    require(width > 0 && height > 0 && pixels.size == width * height)
    val channels = FloatArray(pixels.size * 3)
    pixels.forEachIndexed { index, pixel ->
        val alpha = (pixel ushr 24) / 255f
        // Lift dark midtones before saturation; keep monochrome artwork neutral.
        val r = 0.055f + 0.78f * (((pixel ushr 16) and 255) / 255f * alpha).pow(0.82f)
        val g = 0.055f + 0.78f * (((pixel ushr 8) and 255) / 255f * alpha).pow(0.82f)
        val b = 0.055f + 0.78f * ((pixel and 255) / 255f * alpha).pow(0.82f)
        val gray = r * 0.2126f + g * 0.7152f + b * 0.0722f
        channels[index * 3] = (gray + (r - gray) * 1.4f).coerceIn(0f, 1f)
        channels[index * 3 + 1] = (gray + (g - gray) * 1.4f).coerceIn(0f, 1f)
        channels[index * 3 + 2] = (gray + (b - gray) * 1.4f).coerceIn(0f, 1f)
    }
    // Separable Gaussian, sigma 2.4 texels at the 32x32 working resolution.
    val radius = 7
    val weights = FloatArray(radius * 2 + 1) { exp(-((it - radius) * (it - radius)) / (2f * 2.4f * 2.4f)) }
    val weightSum = weights.sum()
    for (i in weights.indices) weights[i] /= weightSum
    val horizontal = FloatArray(channels.size)
    val blurred = FloatArray(channels.size)
    for (y in 0 until height) for (x in 0 until width) for (channel in 0..2) {
        var sum = 0f
        for (offset in -radius..radius) {
            sum += channels[(y * width + (x + offset).coerceIn(0, width - 1)) * 3 + channel] * weights[offset + radius]
        }
        horizontal[(y * width + x) * 3 + channel] = sum
    }
    for (y in 0 until height) for (x in 0 until width) for (channel in 0..2) {
        var sum = 0f
        for (offset in -radius..radius) {
            sum += horizontal[((y + offset).coerceIn(0, height - 1) * width + x) * 3 + channel] * weights[offset + radius]
        }
        blurred[(y * width + x) * 3 + channel] = sum
    }
    return IntArray(pixels.size) { index ->
        val r = blurred[index * 3]
        val g = blurred[index * 3 + 1]
        val b = blurred[index * 3 + 2]
        // Compress highlights proportionally, preserving hue instead of clipping each channel.
        val peak = maxOf(r, g, b)
        val scale = if (peak > 0.5f) (0.5f + (peak - 0.5f) * 0.38f) / peak else 1f
        fun byte(value: Float) = (value * scale * 255).roundToInt().coerceIn(0, 255)
        (0xff shl 24) or (byte(r) shl 16) or (byte(g) shl 8) or byte(b)
    }
}
