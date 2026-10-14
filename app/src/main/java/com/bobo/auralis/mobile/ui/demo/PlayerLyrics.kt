package com.bobo.auralis.mobile.ui.demo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bobo.auralis.mobile.library.browser.LibraryArtworkSource
import com.bobo.auralis.mobile.lyrics.Lyrics
import com.bobo.auralis.mobile.lyrics.LyricsLoader
import java.io.IOException

private sealed interface LyricsState {
    data object Loading : LyricsState
    data class Ready(val lyrics: Lyrics?) : LyricsState
    data class Error(val message: String) : LyricsState
}

@Composable
internal fun PlayerLyrics(source: LibraryArtworkSource?, positionMs: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val loader = remember(context) { LyricsLoader(context) }
    var retry by remember(source) { mutableIntStateOf(0) }
    key(source?.cacheKey, retry) {
        val state by produceState<LyricsState>(LyricsState.Loading, source, retry) {
            value = try {
                LyricsState.Ready(source?.let { loader.load(it) })
            } catch (_: java.nio.charset.CharacterCodingException) {
                LyricsState.Error("歌词编码不受支持，请使用 UTF-8 或带 BOM 的 UTF-16")
            } catch (error: IOException) {
                LyricsState.Error(error.localizedMessage ?: "歌词读取失败")
            } catch (_: SecurityException) {
                LyricsState.Error("无法访问歌词，请检查曲库目录授权")
            }
        }
        Box(modifier, contentAlignment = Alignment.Center) {
            when (val current = state) {
                LyricsState.Loading -> CircularProgressIndicator()
                is LyricsState.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(current.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { retry++ }) { Text("重试") }
                }
                is LyricsState.Ready -> {
                    val lyrics = current.lyrics
                    if (lyrics == null) Text("暂无歌词", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else LyricsContent(lyrics, positionMs)
                }
            }
        }
    }
}

@Composable
private fun LyricsContent(lyrics: Lyrics, positionMs: Long) {
    val state = rememberLazyListState()
    val active = lyrics.activeIndex(positionMs)
    var follow by remember { mutableStateOf(true) }
    val interactions = state.interactionSource
    LaunchedEffect(interactions) {
        interactions.interactions.collect { if (it is androidx.compose.foundation.interaction.DragInteraction.Start) follow = false }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val padding = maxHeight / 3
        LaunchedEffect(active, follow) {
            if (follow && lyrics.lines.isNotEmpty()) state.animateScrollToItem(active.coerceAtLeast(0))
        }
        LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = padding)) {
            if (lyrics.lines.isEmpty()) {
                item { Text(lyrics.plainText, style = MaterialTheme.typography.titleLarge) }
            } else {
                itemsIndexed(lyrics.lines) { index, line ->
                    Text(
                        text = line.text.ifBlank { "♪" },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = if (index == active) FontWeight.Bold else FontWeight.Normal,
                        color = if (index == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (!follow && lyrics.lines.isNotEmpty()) {
            FilledTonalButton(onClick = { follow = true }, modifier = Modifier.align(Alignment.BottomCenter)) { Text("回到当前歌词") }
        }
    }
}
