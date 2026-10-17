package com.bobo.auralis.mobile.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bobo.auralis.mobile.library.browser.LibraryAlbum
import com.bobo.auralis.mobile.library.browser.LibraryTrack
import com.bobo.auralis.mobile.ui.theme.AuralisThemeTokens

@Composable
fun DemoTracksScreen(
    tracks: List<LibraryTrack>,
    albumsById: Map<String, LibraryAlbum>,
    bottomPadding: Dp,
    listState: LazyListState,
    currentTrackId: String?,
    onTrackClick: (LibraryTrack, List<LibraryTrack>) -> Unit,
    title: String = "歌曲",
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val visibleTracks = remember(tracks, albumsById, searchQuery) {
        filterSongTracks(tracks, albumsById, searchQuery)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding),
    ) {
        item(key = "tracks-title") {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 18.dp, top = 8.dp, end = 18.dp, bottom = 2.dp),
            )
        }
        item(key = "tracks-search") {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                placeholder = { Text("搜索歌曲、艺术家、专辑") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "清除搜索")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            )
        }
        if (visibleTracks.isEmpty()) {
            item(key = "tracks-empty") {
                Text(
                    text = if (searchQuery.isBlank()) "暂无歌曲" else "未找到匹配的歌曲",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        itemsIndexed(visibleTracks, key = { _, track -> track.trackId.toString() }) { index, track ->
            val album = track.albumId?.toString()?.let(albumsById::get)
            val rowColor = if (index % 2 == 0) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
            val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable(enabled = track.isPlayable) { onTrackClick(track, visibleTracks) }
                    .alpha(if (track.isPlayable) 1f else 0.46f)
                    .background(rowColor)
                    .drawBehind {
                        val strokeWidth = 0.5.dp.toPx()
                        val y = size.height - strokeWidth / 2f
                        drawLine(
                            color = dividerColor,
                            start = Offset(80.dp.toPx(), y),
                            end = Offset(size.width - 18.dp.toPx(), y),
                            strokeWidth = strokeWidth,
                        )
                    }
                    .padding(horizontal = 18.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LibraryArtwork(track.artworkSource, album?.albumId?.toString() ?: track.trackId.toString(), Modifier.size(50.dp), cornerRadius = 10)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, fontWeight = if (track.trackId.toString() == currentTrackId) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                    Text(
                        listOf(formatSongArtistCredits(track.artists), track.albumTitle ?: "无专辑").joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(formatDuration(track.durationMs), style = MaterialTheme.typography.bodyMedium, color = AuralisThemeTokens.colors.contentTertiary)
            }
        }
    }
}
