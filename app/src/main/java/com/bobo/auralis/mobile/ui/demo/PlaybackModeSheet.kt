package com.bobo.auralis.mobile.ui.demo

import com.bobo.auralis.mobile.ui.theme.GlassStyle

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bobo.auralis.mobile.playback.PlaybackMode
import com.kyant.backdrop.Backdrop

@Composable
internal fun PlaybackModeSheet(
    selectedMode: PlaybackMode,
    onDismiss: () -> Unit,
    onModeSelected: (PlaybackMode) -> Unit,
    backdrop: Backdrop,
    glassStyle: GlassStyle,
    liquidGlassBlurDp: Float,
) {
    BackHandler(onBack = onDismiss)
    val panelCornerRadius = rememberAdaptiveScreenCornerRadius(
        horizontalInset = 12.dp,
        minimumRadius = 26.dp,
        maximumRadius = 46.dp,
        fallbackRadius = 34.dp,
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.24f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .navigationBarsPadding()
                .auralisGlass(
                    backdrop = backdrop,
                    shape = RoundedCornerShape(panelCornerRadius),
                    surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
                    glassStyle = glassStyle,
                    liquidGlassBlurDp = liquidGlassBlurDp,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "播放模式",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            PlaybackMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onModeSelected(mode) }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundedPlaybackModeIcon(
                        mode = mode,
                        modifier = Modifier.size(25.dp),
                        tint = if (mode == selectedMode) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(mode.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            mode.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (mode == selectedMode) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = "当前模式",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun RoundedPlaybackModeIcon(
    mode: PlaybackMode,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    if (mode == PlaybackMode.ShuffleAlbums) {
        Box(modifier = modifier) {
            Icon(
                imageVector = Icons.Rounded.Album,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.fillMaxSize(0.82f).align(Alignment.TopStart),
            )
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.fillMaxSize(0.48f).align(Alignment.BottomEnd),
            )
        }
    } else {
        val icon = when (mode) {
            PlaybackMode.Sequential -> Icons.AutoMirrored.Rounded.PlaylistPlay
            PlaybackMode.RepeatAll -> Icons.Rounded.Repeat
            PlaybackMode.RepeatOne -> Icons.Rounded.RepeatOne
            PlaybackMode.ShuffleTracks -> Icons.Rounded.Shuffle
            PlaybackMode.ShuffleAlbums -> error("handled above")
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = modifier,
        )
    }
}
