package com.bobo.auralis.mobile.ui.demo

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import com.bobo.auralis.mobile.library.browser.LibraryArtworkSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val dominantColorCache = LruCache<String, Int>(48)

@Composable
internal fun rememberArtworkDominantColor(
    artworkSource: LibraryArtworkSource?,
    identity: String,
    isDark: Boolean,
    fallback: Color,
): Color {
    val context = LocalContext.current
    val cacheKey = artworkSource?.cacheKey ?: "generated:$identity"
    val generated = generatedArtworkPrimaryColor(identity)
    var color by remember(cacheKey, isDark) {
        mutableStateOf(Color(adjustArtworkTrackColor((if (artworkSource == null) generated else fallback).toArgb(), isDark)))
    }

    LaunchedEffect(cacheKey, isDark) {
        val raw = dominantColorCache.get(cacheKey) ?: if (artworkSource == null) {
            generated.toArgb()
        } else {
            loadDominantColor(context, artworkSource) ?: fallback.toArgb()
        }.also { dominantColorCache.put(cacheKey, it) }
        color = Color(adjustArtworkTrackColor(raw, isDark))
    }
    return color
}

private suspend fun loadDominantColor(
    context: android.content.Context,
    artworkSource: LibraryArtworkSource,
): Int? = withContext(Dispatchers.Default) {
    val request = ImageRequest.Builder(context)
        .data(artworkSource)
        .size(64, 64)
        .allowHardware(false)
        .build()
    val image = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image as? BitmapImage
    val bitmap: Bitmap = image?.bitmap ?: return@withContext null
    Palette.from(bitmap).maximumColorCount(12).generate().dominantSwatch?.rgb
}

internal fun adjustArtworkTrackColor(argb: Int, isDark: Boolean): Int {
    val hsl = argbToHsl(argb)
    return hslToArgb(
        hue = hsl.hue,
        saturation = if (hsl.saturation < 0.08f) 0f else hsl.saturation.coerceAtLeast(0.34f),
        lightness = if (isDark) hsl.lightness.coerceIn(0.58f, 0.74f) else hsl.lightness.coerceIn(0.28f, 0.46f),
        alpha = (argb ushr 24) and 0xFF,
    )
}

private data class Hsl(val hue: Float, val saturation: Float, val lightness: Float)

private fun argbToHsl(argb: Int): Hsl {
    val red = ((argb ushr 16) and 0xFF) / 255f
    val green = ((argb ushr 8) and 0xFF) / 255f
    val blue = (argb and 0xFF) / 255f
    val maximum = max(red, max(green, blue))
    val minimum = min(red, min(green, blue))
    val delta = maximum - minimum
    val lightness = (maximum + minimum) / 2f
    if (delta == 0f) return Hsl(0f, 0f, lightness)

    val saturation = delta / (1f - abs(2f * lightness - 1f))
    val hueSector = when (maximum) {
        red -> ((green - blue) / delta) % 6f
        green -> (blue - red) / delta + 2f
        else -> (red - green) / delta + 4f
    }
    val hue = (hueSector * 60f).let { if (it < 0f) it + 360f else it }
    return Hsl(hue, saturation, lightness)
}

private fun hslToArgb(hue: Float, saturation: Float, lightness: Float, alpha: Int): Int {
    val chroma = (1f - abs(2f * lightness - 1f)) * saturation
    val sector = (hue / 60f) % 6f
    val secondary = chroma * (1f - abs(sector % 2f - 1f))
    val (redPrime, greenPrime, bluePrime) = when (sector.toInt()) {
        0 -> Triple(chroma, secondary, 0f)
        1 -> Triple(secondary, chroma, 0f)
        2 -> Triple(0f, chroma, secondary)
        3 -> Triple(0f, secondary, chroma)
        4 -> Triple(secondary, 0f, chroma)
        else -> Triple(chroma, 0f, secondary)
    }
    val match = lightness - chroma / 2f
    val red = ((redPrime + match) * 255f).roundToInt().coerceIn(0, 255)
    val green = ((greenPrime + match) * 255f).roundToInt().coerceIn(0, 255)
    val blue = ((bluePrime + match) * 255f).roundToInt().coerceIn(0, 255)
    return (alpha.coerceIn(0, 255) shl 24) or (red shl 16) or (green shl 8) or blue
}
