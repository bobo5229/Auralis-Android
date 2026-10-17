package com.bobo.auralis.mobile.ui.demo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.bobo.auralis.mobile.library.browser.LibraryTrack
import com.bobo.auralis.mobile.ui.theme.AuralisThemeTokens
import kotlinx.coroutines.launch

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

internal object FloatingDockTokens {
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

@Composable
private fun rememberAdaptiveDockCornerRadius() = rememberAdaptiveScreenCornerRadius(
    horizontalInset = FloatingDockTokens.HorizontalInset,
    minimumRadius = FloatingDockTokens.MinimumCornerRadius,
    maximumRadius = FloatingDockTokens.MaximumCornerRadius,
    fallbackRadius = FloatingDockTokens.FallbackCornerRadius,
)
