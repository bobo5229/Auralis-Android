package com.bobo.auralis.mobile.ui.demo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bobo.auralis.mobile.library.browser.LibraryTrack

@Composable
internal fun GlassMiniPlayer(
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
