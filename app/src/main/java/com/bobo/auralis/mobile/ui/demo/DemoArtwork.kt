package com.bobo.auralis.mobile.ui.demo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.bobo.auralis.mobile.library.browser.LibraryArtworkSource
import kotlin.math.min

@Composable
fun LibraryArtwork(
    artworkSource: LibraryArtworkSource?,
    identity: String,
    modifier: Modifier = Modifier,
    cornerRadius: Int = 18,
) {
    val shape = RoundedCornerShape(cornerRadius.dp)
    Box(modifier = modifier.clip(shape)) {
        GeneratedArtwork(identity, Modifier.fillMaxSize(), cornerRadius)
        if (artworkSource != null) {
            AsyncImage(
                model = artworkSource,
                contentDescription = "专辑封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun GeneratedArtwork(
    identity: String,
    modifier: Modifier = Modifier,
    cornerRadius: Int = 18,
) {
    val palette = artworkPalettes[Math.floorMod(identity.hashCode(), artworkPalettes.size)]
    val motif = ArtworkMotif.entries[Math.floorMod(identity.hashCode(), ArtworkMotif.entries.size)]
    Box(
        modifier = modifier.clip(RoundedCornerShape(cornerRadius.dp)).background(
            brush = Brush.linearGradient(palette.take(2)),
            shape = RoundedCornerShape(cornerRadius.dp),
        ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val side = min(size.width, size.height)
            val accent = palette.getOrElse(2) { Color.White }.copy(alpha = 0.88f)
            when (motif) {
                ArtworkMotif.ORBIT -> {
                    drawCircle(accent, side * 0.25f, Offset(size.width * 0.68f, size.height * 0.34f))
                    drawCircle(Color.Transparent, side * 0.38f, Offset(size.width * 0.28f, size.height * 0.8f))
                    drawCircle(accent.copy(alpha = 0.35f), side * 0.52f, Offset(size.width * 0.2f, size.height * 0.86f))
                }
                ArtworkMotif.SUN -> {
                    drawCircle(accent, side * 0.28f, Offset(size.width * 0.5f, size.height * 0.46f))
                    repeat(8) { index ->
                        rotate(index * 45f, Offset(size.width * 0.5f, size.height * 0.46f)) {
                            drawRect(accent.copy(alpha = 0.55f), Offset(size.width * 0.485f, size.height * 0.04f), Size(side * 0.03f, side * 0.18f))
                        }
                    }
                }
                ArtworkMotif.BARS -> repeat(5) { index ->
                    drawRect(
                        color = if (index % 2 == 0) accent else Color.Black.copy(alpha = 0.45f),
                        topLeft = Offset(size.width * (0.12f + index * 0.16f), size.height * (0.18f + index * 0.06f)),
                        size = Size(side * 0.09f, side * (0.58f - index * 0.05f)),
                    )
                }
                ArtworkMotif.WINDOW -> {
                    drawRect(accent, Offset(size.width * 0.18f, size.height * 0.16f), Size(size.width * 0.64f, size.height * 0.68f))
                    drawLine(palette[0], Offset(size.width * 0.5f, size.height * 0.16f), Offset(size.width * 0.5f, size.height * 0.84f), side * 0.035f)
                    drawLine(palette[0], Offset(size.width * 0.18f, size.height * 0.5f), Offset(size.width * 0.82f, size.height * 0.5f), side * 0.035f)
                }
                ArtworkMotif.WAVE -> repeat(5) { index ->
                    drawArc(
                        color = accent.copy(alpha = 0.9f - index * 0.12f),
                        startAngle = 190f,
                        sweepAngle = 160f,
                        useCenter = false,
                        topLeft = Offset(size.width * (0.04f + index * 0.06f), size.height * (0.18f + index * 0.08f)),
                        size = Size(side * (0.92f - index * 0.12f), side * (0.72f - index * 0.07f)),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = side * 0.035f),
                    )
                }
                ArtworkMotif.BLOOM -> repeat(8) { index ->
                    rotate(index * 45f, Offset(size.width * 0.5f, size.height * 0.5f)) {
                        drawOval(
                            color = accent.copy(alpha = 0.72f),
                            topLeft = Offset(size.width * 0.43f, size.height * 0.12f),
                            size = Size(side * 0.14f, side * 0.42f),
                        )
                    }
                }
            }
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.12f))),
                topLeft = Offset.Zero,
                size = size,
            )
        }
    }
}

private enum class ArtworkMotif { ORBIT, SUN, BARS, WINDOW, WAVE, BLOOM }

private val artworkPalettes = listOf(
    listOf(Color(0xFFDE7048), Color(0xFF532C3D), Color(0xFFF4C77B)),
    listOf(Color(0xFF3D6771), Color(0xFFDCE1D3), Color(0xFF172D35)),
    listOf(Color(0xFFE4D8BD), Color(0xFF2A2925), Color(0xFF9D3F2A)),
    listOf(Color(0xFFF0BB45), Color(0xFF25302B), Color(0xFFF8E8B5)),
    listOf(Color(0xFF272A48), Color(0xFF8EA68D), Color(0xFFD7A6BB)),
    listOf(Color(0xFFED6A3C), Color(0xFF702F2F), Color(0xFFFFD48A)),
)

internal fun generatedArtworkPrimaryColor(identity: String): Color =
    artworkPalettes[Math.floorMod(identity.hashCode(), artworkPalettes.size)].first()
