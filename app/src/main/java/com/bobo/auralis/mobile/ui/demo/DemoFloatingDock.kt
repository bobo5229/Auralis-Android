package com.bobo.auralis.mobile.ui.demo

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.bobo.auralis.mobile.library.browser.LibraryTrack
import com.bobo.auralis.mobile.ui.theme.AuralisThemeTokens
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal enum class DemoGlassStyle(val label: String) {
    Liquid("Liquid Glass"),
    Frosted("磨砂玻璃"),
}

internal enum class DockPlayerState { Expanded, Dragging, Collapsing, Collapsed, Expanding }

@Composable
internal fun DemoFloatingDock(
    backdrop: Backdrop,
    glassStyle: DemoGlassStyle,
    destinations: List<DemoDestination>,
    selectedRoute: String,
    currentTrack: LibraryTrack?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long?,
    isPlayerCollapsed: Boolean,
    onDestinationSelected: (String) -> Unit,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenPlayer: () -> Unit,
    onCollapsePlayer: () -> Unit,
    onExpandPlayer: () -> Unit,
    onPlayerInteractionActiveChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val themeColors = AuralisThemeTokens.colors
    val dockCornerRadius = rememberAdaptiveDockCornerRadius()
    val glassColor = when (glassStyle) {
        DemoGlassStyle.Liquid -> themeColors.glassLiquidTint
        DemoGlassStyle.Frosted -> themeColors.glassFrostedTint
    }
    val transition = remember(currentTrack?.trackId) {
        Animatable(if (isPlayerCollapsed) 1f else 0f)
    }
    var dragDistancePx by remember(currentTrack?.trackId) { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var playerBounds by remember { mutableStateOf<Rect?>(null) }
    var navigationBounds by remember { mutableStateOf<Rect?>(null) }
    var fingerPositionInWindow by remember { mutableStateOf<Offset?>(null) }
    var isFingerOverNavigation by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val localDensity = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val dragOffsetY = remember(currentTrack?.trackId) { Animatable(0f) }

    LaunchedEffect(isPlayerCollapsed, currentTrack?.trackId) {
        transition.animateTo(
            if (isPlayerCollapsed) 1f else 0f,
            animationSpec = tween(FloatingDockTokens.TransitionDurationMs),
        )
        dragOffsetY.snapTo(0f)
        if (isPlayerCollapsed) onPlayerInteractionActiveChanged(false)
    }

    val identity = currentTrack?.albumId?.toString() ?: currentTrack?.trackId?.toString().orEmpty()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val extractedTrackColor = rememberArtworkDominantColor(
        artworkSource = currentTrack?.artworkSource,
        identity = identity,
        isDark = isDark,
        fallback = MaterialTheme.colorScheme.primary,
    )
    val trackColor by animateColorAsState(
        targetValue = extractedTrackColor,
        animationSpec = tween(220),
        label = "compact-player-track-color",
    )
    val progress = playbackProgress(positionMs, durationMs)
    val liftProgress by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = tween(120),
        label = "playbar-lift",
    )

    Box(
        modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(
            horizontal = FloatingDockTokens.HorizontalInset,
            vertical = FloatingDockTokens.VerticalInset,
        ),
    ) {
        Box(Modifier.fillMaxWidth().height(FloatingDockTokens.StackHeight)) {
            currentTrack?.let { track ->
                GlassMiniPlayer(
                    modifier = Modifier.fillMaxWidth().height(FloatingDockTokens.PlayerHeight)
                        .onGloballyPositioned { playerBounds = it.boundsInWindow() }
                        .graphicsLayer {
                            alpha = 1f - transition.value
                            translationY = dragOffsetY.value * (1f - transition.value)
                            val liftedScale = 1f + liftProgress * 0.018f
                            scaleX = liftedScale
                            scaleY = liftedScale
                            shadowElevation = liftProgress * 12.dp.toPx()
                            shape = RoundedCornerShape(dockCornerRadius)
                            clip = false
                        }
                        .then(
                            if (!isPlayerCollapsed && transition.value < 0.05f) {
                                Modifier.pointerInput(track.trackId) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { startPosition ->
                                            isDragging = true
                                            dragDistancePx = 0f
                                            fingerPositionInWindow = playerBounds?.let { bounds ->
                                                Offset(bounds.left + startPosition.x, bounds.top + startPosition.y)
                                            }
                                            isFingerOverNavigation = false
                                            onPlayerInteractionActiveChanged(true)
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragEnd = {
                                            isDragging = false
                                            fingerPositionInWindow = null
                                            if (isFingerOverNavigation) {
                                                isFingerOverNavigation = false
                                                onCollapsePlayer()
                                            } else {
                                                isFingerOverNavigation = false
                                                dragDistancePx = 0f
                                                scope.launch {
                                                    dragOffsetY.animateTo(0f, spring())
                                                    onPlayerInteractionActiveChanged(false)
                                                }
                                            }
                                        },
                                        onDragCancel = {
                                            isDragging = false
                                            fingerPositionInWindow = null
                                            isFingerOverNavigation = false
                                            dragDistancePx = 0f
                                            scope.launch {
                                                dragOffsetY.animateTo(0f, spring())
                                                onPlayerInteractionActiveChanged(false)
                                            }
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragDistancePx += amount.y
                                            fingerPositionInWindow = fingerPositionInWindow?.plus(amount)
                                            isFingerOverNavigation = fingerPositionInWindow?.let { position ->
                                                navigationBounds?.let { bounds ->
                                                    isPointInsideBounds(
                                                        x = position.x,
                                                        y = position.y,
                                                        left = bounds.left,
                                                        top = bounds.top,
                                                        right = bounds.right,
                                                        bottom = bounds.bottom,
                                                    )
                                                } == true
                                            } == true
                                            val maximumDownOffsetPx = with(localDensity) {
                                                FloatingDockTokens.MaximumDownDampedDragOffset.toPx()
                                            }
                                            val maximumUpOffsetPx = with(localDensity) {
                                                FloatingDockTokens.MaximumUpDampedDragOffset.toPx()
                                            }
                                            val dampedOffset = dampedDragOffsetPx(
                                                dragDistancePx = dragDistancePx,
                                                maximumDownOffsetPx = maximumDownOffsetPx,
                                                maximumUpOffsetPx = maximumUpOffsetPx,
                                            )
                                            scope.launch { dragOffsetY.snapTo(dampedOffset) }
                                        },
                                    )
                                }
                            } else Modifier
                        )
                        .auralisGlass(backdrop, RoundedCornerShape(dockCornerRadius), glassColor, glassStyle),
                    track = track,
                    isPlaying = isPlaying,
                    onToggle = onTogglePlayback,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onOpen = onOpenPlayer,
                    enabled = transition.value < 0.05f && !isDragging,
                )
                TransitioningPlayerArtwork(
                    modifier = Modifier.fillMaxSize().zIndex(1f),
                    track = track,
                    identity = identity,
                    transitionProgress = transition.value,
                    dragOffsetYPx = dragOffsetY.value,
                    progress = progress,
                    trackColor = trackColor,
                )
            }
            DockNavigationRow(
                modifier = Modifier.align(Alignment.BottomCenter),
                backdrop = backdrop,
                glassStyle = glassStyle,
                glassColor = glassColor,
                dockCornerRadius = dockCornerRadius,
                destinations = destinations,
                selectedRoute = selectedRoute,
                centerSlotProgress = if (currentTrack == null) 0f else transition.value,
                onDestinationSelected = onDestinationSelected,
                isPlaying = isPlaying,
                onCenterSlotClick = onTogglePlayback,
                onCenterSlotExpand = onExpandPlayer,
                onBoundsChanged = { navigationBounds = it },
            )
        }
    }
}

@Composable
private fun DockNavigationRow(
    backdrop: Backdrop,
    glassStyle: DemoGlassStyle,
    glassColor: Color,
    dockCornerRadius: androidx.compose.ui.unit.Dp,
    destinations: List<DemoDestination>,
    selectedRoute: String,
    centerSlotProgress: Float,
    onDestinationSelected: (String) -> Unit,
    isPlaying: Boolean,
    onCenterSlotClick: () -> Unit,
    onCenterSlotExpand: () -> Unit,
    onBoundsChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val expandThresholdPx = with(LocalDensity.current) { FloatingDockTokens.ExpandSwipeThreshold.toPx() }
    val currentExpand by rememberUpdatedState(onCenterSlotExpand)
    Row(
        modifier = modifier.fillMaxWidth()
            .height(FloatingDockTokens.Height)
            .onGloballyPositioned { onBoundsChanged(it.boundsInWindow()) }
            .auralisGlass(backdrop, RoundedCornerShape(dockCornerRadius), glassColor, glassStyle).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        destinations.forEachIndexed { index, destination ->
            if (index == 2 && centerSlotProgress > 0.001f) {
                Box(
                    Modifier.weight(centerSlotProgress.coerceIn(0.001f, 1f))
                        .height(FloatingDockTokens.ItemHeight)
                        .then(
                            if (centerSlotProgress > 0.99f) {
                                Modifier.clickable(onClick = onCenterSlotClick)
                                    .pointerInput(expandThresholdPx) {
                                        var drag = Offset.Zero
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                drag = Offset.Zero
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                drag += amount
                                            },
                                            onDragEnd = {
                                                if (shouldExpandCollapsedPlayer(drag.x, drag.y, expandThresholdPx)) {
                                                    currentExpand()
                                                }
                                                drag = Offset.Zero
                                            },
                                            onDragCancel = { drag = Offset.Zero },
                                        )
                                    }
                                    .semantics {
                                        contentDescription = if (isPlaying) "暂停，长按向上滑动展开播放器" else "播放，长按向上滑动展开播放器"
                                        customActions = listOf(
                                            CustomAccessibilityAction("展开播放器") {
                                                currentExpand()
                                                true
                                            },
                                        )
                                    }
                            } else Modifier
                        ),
                )
            }
            val selected = destination.route == selectedRoute
            Box(
                modifier = Modifier.weight(1f).height(FloatingDockTokens.ItemHeight)
                    .clip(RoundedCornerShape(FloatingDockTokens.ItemCornerRadius))
                    .clickable { onDestinationSelected(destination.route) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    destination.icon,
                    contentDescription = destination.label,
                    modifier = Modifier.size(FloatingDockTokens.IconSize),
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TransitioningPlayerArtwork(
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

private object FloatingDockTokens {
    val Height = 56.dp
    val PlayerHeight = 66.dp
    val StackGap = 8.dp
    val StackHeight = PlayerHeight + StackGap + Height
    val ItemHeight = 48.dp
    val ItemCornerRadius = 20.dp
    val HorizontalInset = 32.dp
    val IconSize = 32.dp
    val PlayerHorizontalPadding = 9.dp
    val ExpandedArtworkSize = 48.dp
    val CollapsedArtworkSize = 32.dp
    val ProgressTrackSize = 46.dp
    val ProgressTrackStroke = 2.dp
    val MaximumDownDampedDragOffset = 6.dp
    val MaximumUpDampedDragOffset = 2.5.dp
    val ExpandSwipeThreshold = 24.dp
    val VerticalInset = 10.dp
    val MinimumCornerRadius = 20.dp
    val MaximumCornerRadius = Height / 2
    val FallbackCornerRadius = 27.dp
    const val TransitionDurationMs = 280
    const val WavePointCount = 192
}

internal fun shouldExpandCollapsedPlayer(deltaX: Float, deltaY: Float, thresholdPx: Float): Boolean =
    deltaY <= -thresholdPx && -deltaY > abs(deltaX)

internal fun dampedDragOffsetPx(
    dragDistancePx: Float,
    maximumDownOffsetPx: Float,
    maximumUpOffsetPx: Float,
): Float {
    if (dragDistancePx == 0f) return 0f
    val isDownward = dragDistancePx > 0f
    val maximumOffset = if (isDownward) maximumDownOffsetPx else maximumUpOffsetPx
    if (maximumOffset <= 0f) return 0f
    val resistance = if (isDownward) 1f else 2.5f
    val distance = kotlin.math.abs(dragDistancePx)
    val offset = maximumOffset * (1f - 1f / (1f + distance / (maximumOffset * resistance)))
    return if (isDownward) offset else -offset
}

internal fun isPointInsideBounds(
    x: Float,
    y: Float,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
): Boolean = x in left..right && y in top..bottom

internal fun playbackProgress(positionMs: Long, durationMs: Long?): Float {
    val duration = durationMs?.takeIf { it > 0L } ?: return 0f
    return (positionMs.coerceAtLeast(0L).toDouble() / duration.toDouble()).toFloat().coerceIn(0f, 1f)
}

internal fun waveOutwardOffsetFactor(seed: Int, index: Int, pointCount: Int): Float {
    if (pointCount <= 0) return 0f
    val angle = index.toDouble() / pointCount.toDouble() * PI * 2.0
    val phaseA = Math.floorMod(seed, 360) / 360.0 * PI * 2.0
    val phaseB = Math.floorMod(seed / 31, 360) / 360.0 * PI * 2.0
    return (0.55 + sin(angle * 18.0 + phaseA) * 0.30 + sin(angle * 29.0 + phaseB) * 0.15)
        .toFloat()
        .coerceIn(0f, 1f)
}

internal fun waveAccentPeakCount(seed: Int): Int = 1 + Math.floorMod(seed, 2)

internal fun waveAccentFactor(seed: Int, index: Int, pointCount: Int): Float {
    if (pointCount <= 0) return 0f
    val normalizedIndex = Math.floorMod(index, pointCount).toFloat() / pointCount
    val primaryCenter = Math.floorMod(seed.toLong() * 1_103_515_245L + 12_345L, 10_000L) / 10_000f
    val secondaryOffset = 0.38f +
        Math.floorMod(seed.toLong() * 2_654_435_761L + 97L, 2_000L) / 10_000f
    val centers = if (waveAccentPeakCount(seed) == 1) {
        listOf(primaryCenter)
    } else {
        listOf(primaryCenter, (primaryCenter + secondaryOffset) % 1f)
    }
    val halfWidth = 0.04f
    return centers.maxOf { center ->
        val directDistance = abs(normalizedIndex - center)
        val circularDistance = min(directDistance, 1f - directDistance)
        if (circularDistance >= halfWidth) {
            0f
        } else {
            ((1.0 + cos(PI * circularDistance / halfWidth)) * 0.5).toFloat()
        }
    }
}

internal data class WaveTrackPoint(val x: Float, val y: Float)

internal fun roundedWaveTrackPoint(
    seed: Int,
    index: Int,
    pointCount: Int,
    diameter: Float,
): WaveTrackPoint {
    if (pointCount <= 0 || diameter <= 0f) return WaveTrackPoint(0f, 0f)
    val normalized = (index.toFloat() / pointCount).coerceIn(0f, 1f)
    val center = diameter / 2f
    val extent = diameter * (19f / 46f)
    val cornerRadius = diameter * (11f / 46f)
    val straightLength = (extent - cornerRadius) * 2f
    val halfStraightLength = straightLength / 2f
    val cornerLength = (PI.toFloat() / 2f) * cornerRadius
    val perimeter = straightLength * 4f + cornerLength * 4f
    var distance = normalized * perimeter

    fun arcPoint(angle: Float): Pair<WaveTrackPoint, Offset> {
        val cornerCenterX = when {
            angle < 0f -> center + extent - cornerRadius
            angle <= PI.toFloat() / 2f -> center + extent - cornerRadius
            angle <= PI.toFloat() -> center - extent + cornerRadius
            else -> center - extent + cornerRadius
        }
        val cornerCenterY = when {
            angle < 0f -> center - extent + cornerRadius
            angle <= PI.toFloat() / 2f -> center + extent - cornerRadius
            angle <= PI.toFloat() -> center + extent - cornerRadius
            else -> center - extent + cornerRadius
        }
        val normal = Offset(cos(angle.toDouble()).toFloat(), sin(angle.toDouble()).toFloat())
        return WaveTrackPoint(
            x = cornerCenterX + normal.x * cornerRadius,
            y = cornerCenterY + normal.y * cornerRadius,
        ) to normal
    }

    val baseAndNormal: Pair<WaveTrackPoint, Offset> = when {
        distance <= halfStraightLength -> {
            WaveTrackPoint(center + distance, center - extent) to Offset(0f, -1f)
        }
        else -> {
            distance -= halfStraightLength
            when {
                distance <= cornerLength -> arcPoint(-PI.toFloat() / 2f + distance / cornerRadius)
                else -> {
                    distance -= cornerLength
                    when {
                        distance <= straightLength -> WaveTrackPoint(
                            center + extent,
                            center - extent + cornerRadius + distance,
                        ) to Offset(1f, 0f)
                        else -> {
                            distance -= straightLength
                            when {
                                distance <= cornerLength -> arcPoint(distance / cornerRadius)
                                else -> {
                                    distance -= cornerLength
                                    when {
                                        distance <= straightLength -> WaveTrackPoint(
                                            center + extent - cornerRadius - distance,
                                            center + extent,
                                        ) to Offset(0f, 1f)
                                        else -> {
                                            distance -= straightLength
                                            when {
                                                distance <= cornerLength -> arcPoint(PI.toFloat() / 2f + distance / cornerRadius)
                                                else -> {
                                                    distance -= cornerLength
                                                    when {
                                                        distance <= straightLength -> WaveTrackPoint(
                                                            center - extent,
                                                            center + extent - cornerRadius - distance,
                                                        ) to Offset(-1f, 0f)
                                                        else -> {
                                                            distance -= straightLength
                                                            when {
                                                                distance <= cornerLength -> arcPoint(PI.toFloat() + distance / cornerRadius)
                                                                else -> {
                                                                    distance -= cornerLength
                                                                    WaveTrackPoint(
                                                                        center - extent + cornerRadius + distance,
                                                                        center - extent,
                                                                    ) to Offset(0f, -1f)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    val outward = diameter * (1.5f / 46f) * waveOutwardOffsetFactor(seed, index, pointCount) +
        diameter * (1.35f / 46f) * waveAccentFactor(seed, index, pointCount)
    return WaveTrackPoint(
        x = baseAndNormal.first.x + baseAndNormal.second.x * outward,
        y = baseAndNormal.first.y + baseAndNormal.second.y * outward,
    )
}

internal fun navigationSlotCenters(slotCount: Int, width: Float): List<Float> {
    if (slotCount <= 0 || width <= 0f) return emptyList()
    val slotWidth = width / slotCount
    return List(slotCount) { index -> slotWidth * (index + 0.5f) }
}

@Composable
private fun rememberAdaptiveDockCornerRadius() = rememberAdaptiveScreenCornerRadius(
    horizontalInset = FloatingDockTokens.HorizontalInset,
    minimumRadius = FloatingDockTokens.MinimumCornerRadius,
    maximumRadius = FloatingDockTokens.MaximumCornerRadius,
    fallbackRadius = FloatingDockTokens.FallbackCornerRadius,
)

@Composable
internal fun rememberAdaptiveScreenCornerRadius(
    horizontalInset: androidx.compose.ui.unit.Dp,
    minimumRadius: androidx.compose.ui.unit.Dp,
    maximumRadius: androidx.compose.ui.unit.Dp,
    fallbackRadius: androidx.compose.ui.unit.Dp,
) = with(LocalDensity.current) {
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    var screenCornerRadiusPx by remember(view) { mutableStateOf<Int?>(null) }

    LaunchedEffect(
        view,
        configuration.orientation,
        configuration.screenWidthDp,
        configuration.screenHeightDp,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            screenCornerRadiusPx = null
            return@LaunchedEffect
        }

        repeat(3) {
            withFrameNanos { }
            readBottomScreenCornerRadiusPx(view.rootWindowInsets)?.let { radius ->
                screenCornerRadiusPx = radius
                return@LaunchedEffect
            }
        }
        screenCornerRadiusPx = null
    }

    calculateDockCornerRadiusDp(
        screenCornerRadiusDp = screenCornerRadiusPx?.toDp()?.value,
        horizontalInsetDp = horizontalInset.value,
        minimumRadiusDp = minimumRadius.value,
        maximumRadiusDp = maximumRadius.value,
        fallbackRadiusDp = fallbackRadius.value,
    ).dp
}

private fun readBottomScreenCornerRadiusPx(windowInsets: android.view.WindowInsets?): Int? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || windowInsets == null) return null
    val radii = listOfNotNull(
        windowInsets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius,
        windowInsets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius,
    ).filter { it > 0 }
    return radii.minOrNull()
}

internal fun calculateDockCornerRadiusDp(
    screenCornerRadiusDp: Float?,
    horizontalInsetDp: Float = 12f,
    minimumRadiusDp: Float = 20f,
    maximumRadiusDp: Float = 35f,
    fallbackRadiusDp: Float = 27f,
): Float {
    if (screenCornerRadiusDp == null || screenCornerRadiusDp <= 0f) return fallbackRadiusDp
    return (screenCornerRadiusDp - horizontalInsetDp).coerceIn(minimumRadiusDp, maximumRadiusDp)
}

@Composable
private fun GlassMiniPlayer(
    track: LibraryTrack,
    isPlaying: Boolean,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.then(if (enabled) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(horizontal = FloatingDockTokens.PlayerHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(FloatingDockTokens.ExpandedArtworkSize))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artistLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onPrevious, enabled = enabled) {
            Icon(
                Icons.Rounded.FastRewind,
                contentDescription = "上一首",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onToggle, enabled = enabled) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onNext, enabled = enabled) {
            Icon(
                Icons.Rounded.FastForward,
                contentDescription = "下一首",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

internal fun Modifier.auralisGlass(
    backdrop: Backdrop,
    shape: Shape,
    surfaceColor: Color,
    glassStyle: DemoGlassStyle,
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        when (glassStyle) {
            DemoGlassStyle.Liquid -> {
                vibrancy()
                blur(2.dp.toPx())
                lens(
                    refractionHeight = size.minDimension * 0.28f,
                    refractionAmount = size.minDimension * 0.58f,
                    depthEffect = true,
                    chromaticAberration = true,
                )
            }
            DemoGlassStyle.Frosted -> {
                vibrancy()
                blur(7.dp.toPx())
                lens(12.dp.toPx(), 20.dp.toPx())
            }
        }
    },
    onDrawSurface = { drawRect(surfaceColor) },
)
