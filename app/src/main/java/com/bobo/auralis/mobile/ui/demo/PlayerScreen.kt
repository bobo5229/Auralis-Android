package com.bobo.auralis.mobile.ui.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bobo.auralis.mobile.library.browser.LibraryAlbum
import com.bobo.auralis.mobile.library.browser.LibraryTrack
import com.bobo.auralis.mobile.playback.PlaybackMode

@Composable
internal fun DemoPlayerScreen(
    album: LibraryAlbum?,
    track: LibraryTrack,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long?,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    playbackMode: PlaybackMode,
    onPlaybackModeClick: () -> Unit,
) {
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    val effectiveDuration = durationMs?.takeIf { it > 0 } ?: 0L
    val progress = if (effectiveDuration > 0) {
        (positionMs.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
            Spacer(Modifier.weight(1f))
            Text("正在播放", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showLyrics = !showLyrics }) {
                Icon(if (showLyrics) Icons.Rounded.Album else Icons.Rounded.Lyrics, contentDescription = if (showLyrics) "显示封面" else "显示歌词")
            }
        }
        Spacer(Modifier.height(20.dp))
        if (showLyrics) {
            PlayerLyrics(track.audioSource, positionMs, Modifier.fillMaxWidth().weight(1f))
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                LibraryArtwork(track.artworkSource, album?.albumId?.toString() ?: track.trackId.toString(), Modifier.size(minOf(maxWidth, maxHeight)), cornerRadius = 26)
            }
        }
        Spacer(Modifier.height(28.dp))
        Column(Modifier.fillMaxWidth()) {
            Text(track.title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artistLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(track.albumTitle ?: "无专辑", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(18.dp))
        Slider(
            value = progress,
            onValueChange = { fraction -> if (effectiveDuration > 0) onSeek((fraction * effectiveDuration).toLong()) },
            enabled = effectiveDuration > 0,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(positionMs), style = MaterialTheme.typography.bodyMedium)
            Text(formatDuration(effectiveDuration.takeIf { it > 0 }), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPlaybackModeClick) {
                RoundedPlaybackModeIcon(
                    mode = playbackMode,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(27.dp)
                        .semantics { contentDescription = "播放模式：${playbackMode.label}" },
                )
            }
            IconButton(onClick = onPrevious) {
                Icon(
                    Icons.Rounded.FastRewind,
                    contentDescription = "上一首",
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            FilledIconButton(onClick = onToggle, modifier = Modifier.size(68.dp)) {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (isPlaying) "暂停" else "播放", modifier = Modifier.size(34.dp))
            }
            IconButton(onClick = onNext) {
                Icon(
                    Icons.Rounded.FastForward,
                    contentDescription = "下一首",
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.size(48.dp))
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { showLyrics = !showLyrics }) {
            Icon(Icons.Rounded.Lyrics, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (showLyrics) "查看封面" else "查看歌词")
        }
        }
    }
}
