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
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import com.bobo.auralis.mobile.library.browser.LibraryArtworkSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        val raw = dominantColorCache.get(cacheKey) ?: (if (artworkSource == null) {
            generated.toArgb()
        } else {
            loadDominantColor(context, artworkSource)
        })?.also { dominantColorCache.put(cacheKey, it) } ?: fallback.toArgb()
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
        .size(128, 128)
        .allowHardware(false)
        .build()
    val image = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image as? BitmapImage
    val bitmap: Bitmap = image?.bitmap ?: return@withContext null
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    extractArtworkSeedColor(pixels)
}
