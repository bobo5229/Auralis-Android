package com.bobo.auralis.mobile.ui.demo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.lerp
import com.bobo.auralis.mobile.library.browser.LibraryTrack

@Composable
internal fun TransitioningPlayerArtwork(
    track: LibraryTrack,
    identity: String,
    transitionProgress: Float,
    dragOffsetYPx: Float,
    progress: Float,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val fraction = transitionProgress.coerceIn(0f, 1f)
        val containerSize = lerp(FloatingDockTokens.ExpandedArtworkSize, FloatingDockTokens.ProgressTrackSize, fraction)
        val artworkSize = lerp(FloatingDockTokens.ExpandedArtworkSize, FloatingDockTokens.CollapsedArtworkSize, fraction)
        val sourceCenterX = FloatingDockTokens.PlayerHorizontalPadding + FloatingDockTokens.ExpandedArtworkSize / 2
        val sourceCenterY = FloatingDockTokens.PlayerHeight / 2
        val targetCenterX = maxWidth / 2
        val targetCenterY = FloatingDockTokens.StackHeight - FloatingDockTokens.Height / 2
        val centerX = lerp(sourceCenterX, targetCenterX, fraction)
        val dragOffsetY = with(LocalDensity.current) { dragOffsetYPx.toDp() }
        val centerY = lerp(sourceCenterY + dragOffsetY, targetCenterY, fraction)

        Box(
            modifier = Modifier
                .offset(x = centerX - containerSize / 2, y = centerY - containerSize / 2)
                .size(containerSize)
                .graphicsLayer { alpha = 1f },
            contentAlignment = Alignment.Center,
        ) {
            WaveProgressTrack(
                identity = identity,
                progress = progress,
                playedColor = trackColor,
                alpha = fraction,
                modifier = Modifier.size(FloatingDockTokens.ProgressTrackSize),
            )
            LibraryArtwork(
                artworkSource = track.artworkSource,
                identity = identity,
                modifier = Modifier.size(artworkSize),
                cornerRadius = if (fraction > 0.5f) 8 else 11,
            )
        }
    }
}

@Composable
private fun WaveProgressTrack(
    identity: String,
    progress: Float,
    playedColor: Color,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.graphicsLayer { this.alpha = alpha }) {
        val path = irregularWavePath(identity.hashCode(), size.minDimension)
        val measure = PathMeasure().apply { setPath(path, true) }
        drawPath(
            path = path,
            color = playedColor.copy(alpha = 0.28f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = FloatingDockTokens.ProgressTrackStroke.toPx()),
        )
        if (progress > 0f && measure.length > 0f) {
            val playedPath = Path()
            measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), playedPath, true)
            drawPath(
                path = playedPath,
                color = playedColor.copy(alpha = 0.96f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = FloatingDockTokens.ProgressTrackStroke.toPx()),
            )
        }
    }
}

private fun irregularWavePath(seed: Int, diameter: Float): Path {
    val path = Path()
    repeat(FloatingDockTokens.WavePointCount + 1) { index ->
        val point = roundedWaveTrackPoint(
            seed = seed,
            index = index,
            pointCount = FloatingDockTokens.WavePointCount,
            diameter = diameter,
        )
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    return path
}
