package com.bobo.auralis.mobile.ui.demo

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.LruCache
import androidx.annotation.RequiresApi
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import com.bobo.auralis.mobile.library.browser.LibraryArtworkSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

// Small, softened textures only; never retain full-size artwork or mutate Coil's bitmaps.
private val backgroundTextures = LruCache<String, Bitmap>(12)

@Composable
internal fun PlayerBackground(
    source: LibraryArtworkSource?,
    identity: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val key = source?.cacheKey ?: "generated:$identity"
    val fallback = remember(identity) {
        Color(prepareBackgroundPixels(intArrayOf(generatedArtworkPrimaryColor(identity).toArgb()), 1, 1)[0])
    }
    var texture by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(key) {
        texture = backgroundTextures.get(key) ?: withContext(Dispatchers.Default) {
            val request = source?.let {
                ImageRequest.Builder(context).data(it).size(128).allowHardware(false).build()
            }
            val bitmap = request?.let {
                ((SingletonImageLoader.get(context).execute(it) as? SuccessResult)?.image as? BitmapImage)?.bitmap
            }
            val softened = if (bitmap != null) {
                val small = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
                val pixels = IntArray(32 * 32)
                small.getPixels(pixels, 0, 32, 0, 0, 32, 32)
                Bitmap.createBitmap(prepareBackgroundPixels(pixels, 32, 32), 32, 32, Bitmap.Config.ARGB_8888)
            } else {
                Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(fallback.toArgb())
                }
            }
            backgroundTextures.put(key, softened)
            softened
        }
    }
    Crossfade(texture, modifier, animationSpec = tween(700), label = "playerArtworkBackground") { bitmap ->
        if (bitmap != null && Build.VERSION.SDK_INT >= 33) {
            FlowingArtwork(bitmap, isPlaying && bitmap.width > 2, Modifier.fillMaxSize())
        } else {
            val image = remember(bitmap) { bitmap?.asImageBitmap() }
            Canvas(Modifier.fillMaxSize()) {
                drawRect(fallback)
                if (image != null) drawImage(image, dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Medium)
                drawRect(Brush.verticalGradient(listOf(
                    Color.Black.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.20f),
                )))
            }
        }
    }
}


@RequiresApi(33)
@Composable
internal fun FlowingArtwork(bitmap: Bitmap, isPlaying: Boolean, modifier: Modifier) {
    val shader = remember(bitmap) {
        RuntimeShader(ArtworkFlowShader).apply {
            setInputShader("artwork", BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
            })
            setFloatUniform("textureSize", bitmap.width.toFloat(), bitmap.height.toFloat())
        }
    }
    val brush = remember(shader) { ShaderBrush(shader) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying, lifecycle) {
        if (!isPlaying) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (isActive) {
                if (!ValueAnimator.areAnimatorsEnabled()) {
                    previous = 0L
                    delay(250)
                    continue
                }
                withFrameNanos { now ->
                    if (previous != 0L && ValueAnimator.areAnimatorsEnabled()) {
                        time.floatValue += ((now - previous) / 1_000_000_000f).coerceAtMost(0.1f)
                    }
                    previous = now
                }
                delay(32) // Ambient motion does not need display-refresh-rate updates.
            }
        }
    }
    Canvas(modifier) {
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("time", time.floatValue)
        drawRect(brush)
    }
}
