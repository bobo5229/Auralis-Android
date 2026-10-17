package com.bobo.auralis.mobile.ui.demo

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import com.bobo.auralis.mobile.library.browser.LibraryAlbum
import com.bobo.auralis.mobile.ui.theme.AlbumLayoutAnimationStyle
import com.bobo.auralis.mobile.ui.theme.AlbumLayoutMode
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun DemoAlbumsScreen(
    albums: List<LibraryAlbum>,
    bottomPadding: Dp,
    gridState: LazyGridState,
    layoutMode: AlbumLayoutMode,
    animationStyle: AlbumLayoutAnimationStyle,
    onLayoutModeSelected: (AlbumLayoutMode) -> Unit,
    onAlbumClick: (String) -> Unit,
) {
    val currentLayoutMode = rememberUpdatedState(layoutMode)
    val currentOnLayoutModeSelected = rememberUpdatedState(onLayoutModeSelected)
    var renderedLayoutMode by remember { mutableStateOf(layoutMode) }
    val contentAlpha = remember { Animatable(1f) }
    LaunchedEffect(layoutMode, animationStyle) {
        when (animationStyle) {
            AlbumLayoutAnimationStyle.Fade -> {
                if (renderedLayoutMode != layoutMode) {
                    contentAlpha.animateTo(0f, tween(durationMillis = 90))
                    renderedLayoutMode = layoutMode
                    contentAlpha.snapTo(0f)
                    contentAlpha.animateTo(1f, tween(durationMillis = 150))
                } else {
                    contentAlpha.animateTo(1f, tween(durationMillis = 120))
                }
            }
            AlbumLayoutAnimationStyle.Reflow -> {
                contentAlpha.snapTo(1f)
                renderedLayoutMode = layoutMode
            }
        }
    }
    val columnCount = renderedLayoutMode.gridColumns ?: 1
    val horizontalSpacing = when (columnCount) {
        1 -> 0.dp
        2 -> 14.dp
        else -> 10.dp
    }
    val verticalSpacing = if (renderedLayoutMode.isGrid) 22.dp else 0.dp
    LazyVerticalGrid(
        columns = GridCells.Fixed(columnCount),
        state = gridState,
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            awaitEachGesture {
                // A menu can change density while this coroutine is waiting.
                // Snapshot the mode when a new gesture actually starts.
                awaitFirstDown(requireUnconsumed = false)
                var gestureMode = currentLayoutMode.value
                var accumulatedZoom = 1f
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.count { it.pressed } >= 2) {
                        accumulatedZoom *= event.calculateZoom()
                        val densitySteps = albumLayoutDensitySteps(accumulatedZoom)
                        if (densitySteps != 0) {
                            val targetMode = gestureMode.shiftDensity(densitySteps)
                            if (targetMode != gestureMode) {
                                gestureMode = targetMode
                                currentOnLayoutModeSelected.value(targetMode)
                            }
                            accumulatedZoom = 1f
                        }
                        event.changes.forEach { it.consume() }
                    }
                    if (event.changes.none { it.pressed }) break
                }
            }
        },
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
    ) {
        item(key = "albums-title", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = "专辑",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        items(albums, key = { it.albumId.toString() }) { album ->
            AlbumMorphingItem(
                album = album,
                layoutMode = renderedLayoutMode,
                animateReflow = animationStyle == AlbumLayoutAnimationStyle.Reflow,
                onAlbumClick = onAlbumClick,
                modifier = Modifier.alpha(contentAlpha.value).animateItem(
                    fadeInSpec = null,
                    placementSpec = if (animationStyle == AlbumLayoutAnimationStyle.Reflow) {
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        )
                    } else {
                        null
                    },
                    fadeOutSpec = null,
                ),
            )
        }
    }
}

private const val AlbumLayoutZoomStep = 1.18

internal fun albumLayoutDensitySteps(zoom: Float): Int {
    if (!zoom.isFinite() || zoom <= 0f) return 0
    val rawSteps = -ln(zoom.toDouble()) / ln(AlbumLayoutZoomStep)
    return when {
        rawSteps >= 1.0 -> floor(rawSteps).toInt()
        rawSteps <= -1.0 -> ceil(rawSteps).toInt()
        else -> 0
    }
}

private data class AlbumItemLayout(val mode: AlbumLayoutMode, val width: Dp) {
    val artworkSize: Dp get() = when {
        mode.isGrid -> width
        mode == AlbumLayoutMode.Coverless -> 0.dp
        else -> 56.dp
    }
    val gap: Dp get() = when {
        mode.isGrid && (mode.gridColumns ?: 1) >= 4 -> 6.dp
        mode.isGrid -> 9.dp
        mode == AlbumLayoutMode.Coverless -> 0.dp
        else -> 12.dp
    }
    val verticalPadding: Dp get() = when {
        mode.isGrid -> 0.dp
        mode == AlbumLayoutMode.Coverless -> 10.dp
        else -> 7.dp
    }
}

@Composable
private fun AlbumMorphingItem(
    album: LibraryAlbum,
    layoutMode: AlbumLayoutMode,
    animateReflow: Boolean,
    onAlbumClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().clickable { onAlbumClick(album.albumId.toString()) },
    ) {
        val isGrid = layoutMode.isGrid
        val targetLayout = AlbumItemLayout(layoutMode, maxWidth)
        val transition = updateTransition(targetLayout, label = "album-layout")
        val layoutAnimation = if (animateReflow) {
            spring<Dp>(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
        } else {
            snap()
        }
        val artworkSize by transition.animateDp(
            transitionSpec = { layoutAnimation },
            label = "album-artwork-size",
        ) { it.artworkSize }
        val rowProgress by transition.animateFloat(
            transitionSpec = {
                if (animateReflow) {
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    )
                } else {
                    snap()
                }
            },
            label = "album-row-progress",
        ) { if (it.mode.isGrid) 0f else 1f }
        val gap by transition.animateDp(
            transitionSpec = { layoutAnimation },
            label = "album-content-gap",
        ) { it.gap }
        val verticalPadding by transition.animateDp(
            transitionSpec = { layoutAnimation },
            label = "album-vertical-padding",
        ) { it.verticalPadding }
        val isMorphing = animateReflow && (transition.isRunning || transition.currentState != transition.targetState)
        // Measure artwork at a stable endpoint size. Only the layer transform
        // changes per frame; retargeting preserves the spring's current value.
        val artworkMeasureSize = maxOf(transition.currentState.artworkSize, targetLayout.artworkSize)

        Layout(
            content = {
                LibraryArtwork(
                    artworkSource = album.artworkSource,
                    identity = album.albumId.toString(),
                    cornerRadius = if (isGrid) 18 else 10,
                )
                AlbumText(
                    album = album,
                    compact = isGrid && (layoutMode.gridColumns ?: 1) >= 4,
                    showDate = isGrid && layoutMode.gridColumns == 1,
                )
            },
            // Allow visual bounds to cross the target cell while transforming.
            modifier = Modifier.fillMaxWidth().then(if (isMorphing) Modifier else Modifier.clipToBounds()),
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val artworkMeasurePx = artworkMeasureSize.roundToPx().coerceAtLeast(0)
            val targetArtworkPx = targetLayout.artworkSize.roundToPx().coerceAtLeast(0)
            val targetGapPx = targetLayout.gap.roundToPx()
            val targetPaddingPx = targetLayout.verticalPadding.roundToPx()
            val textWidth = if (isGrid) width else (width - targetArtworkPx - targetGapPx).coerceAtLeast(0)
            val artworkPlaceable = measurables[0].measure(
                Constraints.fixed(artworkMeasurePx, artworkMeasurePx),
            )
            val textPlaceable = measurables[1].measure(
                Constraints(maxWidth = textWidth),
            )

            val targetRowHeight = max(targetArtworkPx, textPlaceable.height) + targetPaddingPx * 2
            val height = if (isGrid) targetArtworkPx + targetGapPx + textPlaceable.height else targetRowHeight
            val rtl = layoutDirection == LayoutDirection.Rtl
            val densityScale = density

            layout(width, height) {
                // Read animated values inside layer blocks, keeping measurement
                // and paragraph layout out of the animation's per-frame path.
                artworkPlaceable.placeRelativeWithLayer(0, 0) {
                    val visualSize = artworkSize.value * densityScale
                    val scale = if (artworkMeasurePx > 0) visualSize / artworkMeasurePx else 0f
                    scaleX = scale.coerceAtLeast(0f)
                    scaleY = scaleX
                    transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0f)
                    translationY = verticalPadding.value * densityScale
                }
                textPlaceable.placeRelativeWithLayer(0, 0) {
                    val visualSize = artworkSize.value * densityScale
                    val visualGap = gap.value * densityScale
                    val padding = verticalPadding.value * densityScale
                    val progress = rowProgress.coerceIn(0f, 1f)
                    val rowHeight = max(visualSize, textPlaceable.height.toFloat()) + padding * 2
                    val textX = (visualSize + visualGap) * progress
                    translationX = if (rtl) -textX else textX
                    translationY = (visualSize + visualGap) * (1f - progress) +
                        ((rowHeight - textPlaceable.height) / 2f).coerceAtLeast(0f) * progress
                }
            }
        }
    }
}

@Composable
private fun AlbumText(album: LibraryAlbum, compact: Boolean = false, showDate: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                album.title,
                modifier = Modifier.weight(1f).alignByBaseline(),
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showDate) {
                Text(
                    album.date ?: "未知日期",
                    modifier = Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        Text(
            album.albumArtistLabel,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun lerpInt(start: Int, end: Int, fraction: Float): Int =
    (start + (end - start) * fraction).roundToInt()
