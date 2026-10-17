package com.bobo.auralis.mobile.ui.demo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop

@Composable
internal fun DockNavigationRow(
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

internal fun navigationSlotCenters(slotCount: Int, width: Float): List<Float> {
    if (slotCount <= 0 || width <= 0f) return emptyList()
    val slotWidth = width / slotCount
    return List(slotCount) { index -> slotWidth * (index + 0.5f) }
}
