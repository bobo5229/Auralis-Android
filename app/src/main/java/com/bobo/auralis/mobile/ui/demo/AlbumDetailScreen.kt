package com.bobo.auralis.mobile.ui.demo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bobo.auralis.mobile.library.browser.LibraryAlbum
import com.bobo.auralis.mobile.library.browser.LibraryTrack
import com.bobo.auralis.mobile.ui.theme.AuralisThemeTokens

@Composable
fun DemoAlbumDetailScreen(
    album: LibraryAlbum,
    bottomPadding: Dp,
    currentTrackId: String?,
    onPlayAlbum: () -> Unit,
    onTrackClick: (LibraryTrack) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding)) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LibraryArtwork(album.artworkSource, album.albumId.toString(), Modifier.fillMaxWidth(0.74f).aspectRatio(1f), cornerRadius = 22)
                Spacer(Modifier.height(22.dp))
                Text(album.title, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    formatSongArtistCredits(album.albumArtists),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("${album.date ?: "未知日期"} · ${album.tracks.size} 首曲目", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onPlayAlbum,
                    enabled = album.tracks.any { it.isPlayable },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("播放专辑")
                }
            }
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        items(album.tracks, key = { it.trackId.toString() }) { track ->
            DemoTrackRow(track, track.trackId.toString() == currentTrackId) { onTrackClick(track) }
        }
    }
}

@Composable
private fun DemoTrackRow(track: LibraryTrack, isCurrent: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = track.isPlayable, onClick = onClick)
            .alpha(if (track.isPlayable) 1f else 0.46f).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            track.trackNumber?.toString()?.padStart(2, '0') ?: "—",
            modifier = Modifier.width(34.dp),
            color = if (isCurrent) MaterialTheme.colorScheme.primary else AuralisThemeTokens.colors.contentTertiary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Column(Modifier.weight(1f)) {
            Text(track.title, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium)
            Text(
                track.unavailableReason ?: formatSongArtistCredits(track.artists),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isCurrent) Icon(Icons.Rounded.GraphicEq, contentDescription = "正在播放", tint = MaterialTheme.colorScheme.primary)
        else Text(formatDuration(track.durationMs), style = MaterialTheme.typography.bodyMedium, color = AuralisThemeTokens.colors.contentTertiary)
    }
}
