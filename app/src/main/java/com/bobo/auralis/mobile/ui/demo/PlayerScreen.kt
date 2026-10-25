package com.bobo.auralis.mobile.ui.demo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobo.auralis.mobile.library.browser.LibraryAlbum
import com.bobo.auralis.mobile.library.browser.LibraryTrack
import com.bobo.auralis.mobile.playback.PlaybackMode

@OptIn(ExperimentalMaterial3Api::class)
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
    showPauseButton: Boolean = isPlaying,
) {
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    val effectiveDuration = durationMs?.takeIf { it > 0 } ?: 0L
    val progress = if (effectiveDuration > 0) {
        (positionMs.toFloat() / effectiveDuration).coerceIn(0f, 1f)
    } else 0f
    val identity = album?.albumId?.toString() ?: track.albumId?.toString() ?: track.trackId.toString()
    val foreground = Color.White
    val secondary = foreground.copy(alpha = 0.76f)
    val playerColors = MaterialTheme.colorScheme.copy(
        primary = foreground, onSurface = foreground, onSurfaceVariant = secondary,
    )
    MaterialTheme(colorScheme = playerColors) {
        CompositionLocalProvider(LocalContentColor provides foreground) {
            BoxWithConstraints(
                Modifier.fillMaxSize(),
            ) {
                PlayerBackground(track.artworkSource, identity, isPlaying, Modifier.matchParentSize())
                val compact = maxHeight < 640.dp
                val songStyle = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.SansSerif, fontSize = 18.sp, lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp,
                )
                val timeStyle = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.SansSerif, fontSize = 10.sp, lineHeight = 14.sp,
                    fontWeight = FontWeight.Normal, letterSpacing = 0.sp,
                )
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "返回", tint = secondary)
                    }
                    // Flexible artwork region keeps the transport controls on screen on shorter phones.
                    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = BiasAlignment(0f, if (compact) 0f else 0.52f)) {
                        if (showLyrics) {
                            PlayerLyrics(track.audioSource, positionMs, Modifier.fillMaxSize())
                        } else {
                            LibraryArtwork(
                                track.artworkSource, identity,
                                Modifier.size(minOf(maxWidth, maxHeight)), cornerRadius = 8,
                            )
                        }
                    }
                    Spacer(Modifier.height(if (compact) 20.dp else 32.dp))
                    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                        Text(
                            track.title, style = songStyle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            track.artistLabel, style = songStyle.copy(fontWeight = FontWeight.Normal),
                            color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Slider(
                        value = progress,
                        onValueChange = { if (effectiveDuration > 0) onSeek((it * effectiveDuration).toLong()) },
                        enabled = effectiveDuration > 0,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).semantics { contentDescription = "播放进度" },
                        thumb = {},
                        track = { state ->
                            Canvas(Modifier.fillMaxWidth().height(5.dp)) {
                                val radius = CornerRadius(size.height / 2)
                                drawRoundRect(foreground.copy(alpha = 0.24f), cornerRadius = radius)
                                drawRoundRect(secondary, size = Size(size.width * state.value, size.height), cornerRadius = radius)
                            }
                        },
                    )
                    // Bring labels closer without shrinking the slider's touch area.
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).offset(y = (-8).dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatDuration(positionMs), style = timeStyle, color = secondary)
                        Text(
                            if (effectiveDuration > 0) "−${formatDuration((effectiveDuration - positionMs).coerceAtLeast(0))}" else "—:—",
                            style = timeStyle, color = secondary,
                        )
                    }
                    Spacer(Modifier.height(if (compact) 16.dp else 32.dp))
                    Row(
                        Modifier.fillMaxWidth().height(76.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onPrevious, modifier = Modifier.size(64.dp)) {
                            Icon(Icons.Rounded.FastRewind, "上一首", Modifier.size(60.dp))
                        }
                        IconButton(onClick = onToggle, modifier = Modifier.size(80.dp)) {
                            Icon(if (showPauseButton) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                if (showPauseButton) "暂停" else "播放", Modifier.size(64.dp))
                        }
                        IconButton(onClick = onNext, modifier = Modifier.size(64.dp)) {
                            Icon(Icons.Rounded.FastForward, "下一首", Modifier.size(60.dp))
                        }
                    }
                    Spacer(Modifier.height(if (compact) 16.dp else 60.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        IconButton(onClick = { showLyrics = !showLyrics }) {
                            Icon(if (showLyrics) Icons.Rounded.Album else Icons.Rounded.Lyrics,
                                if (showLyrics) "显示封面" else "显示歌词", Modifier.size(26.dp), tint = secondary)
                        }
                        IconButton(onClick = onPlaybackModeClick) {
                            RoundedPlaybackModeIcon(
                                playbackMode, secondary,
                                Modifier.size(26.dp).semantics { contentDescription = "播放模式：${playbackMode.label}" },
                            )
                        }
                    }
                }
            }
        }
    }
}
