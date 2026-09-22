package com.bobo.auralis.mobile.ui.demo

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.bobo.auralis.mobile.library.browser.LibraryAlbum
import com.bobo.auralis.mobile.library.browser.LibraryTrack
import com.bobo.auralis.mobile.playback.PlaybackMode
import com.bobo.auralis.mobile.ui.theme.AuralisThemeTokens
import com.bobo.auralis.mobile.ui.theme.AlbumLayoutAnimationStyle
import com.bobo.auralis.mobile.ui.theme.AlbumLayoutMode
import com.kyant.backdrop.Backdrop
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun LibraryLoadingScreen() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun DemoPlaceholderScreen(label: String, bottomPadding: Dp) {
    Box(
        modifier = Modifier.fillMaxSize().padding(bottom = bottomPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
fun DemoLibraryScreen(
    bottomPadding: Dp,
    onAlbumsClick: () -> Unit,
    onTracksClick: () -> Unit,
    onPlaylistsClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, bottom = bottomPadding),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        LibraryCategoryRow("专辑", Icons.Rounded.Album, onAlbumsClick)
        LibraryCategoryDivider()
        LibraryCategoryRow("歌曲", Icons.Rounded.LibraryMusic, onTracksClick)
        LibraryCategoryDivider()
        LibraryCategoryRow("歌单", Icons.AutoMirrored.Rounded.QueueMusic, onPlaylistsClick)
    }
}

@Composable
private fun LibraryCategoryDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 38.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
    )
}

@Composable
private fun LibraryCategoryRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun DemoWelcomeScreen(onManageLibrary: () -> Unit) {
    val primaryDecoration = MaterialTheme.colorScheme.primaryContainer
    val secondaryDecoration = MaterialTheme.colorScheme.secondaryContainer
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(primaryDecoration, size.width * 0.72f, Offset(size.width * 0.92f, size.height * 0.12f))
            drawCircle(secondaryDecoration, size.width * 0.5f, Offset(size.width * 0.04f, size.height * 0.78f))
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 42.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("AURALIS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(54.dp))
                Text("你的音乐，\n在这里成为收藏。", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(18.dp))
                Text(
                    "只读取你选择的本地目录。没有账号，没有云端，也不会修改音乐文件。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureLine("多目录曲库", "由你决定音乐来自哪里")
                FeatureLine("专辑优先", "保留发行版本和完整元数据")
                FeatureLine("纯本地播放", "离线也拥有完整体验")
                Spacer(Modifier.height(12.dp))
                Button(onClick = onManageLibrary, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("选择并扫描音乐目录")
                }
                Text(
                    "目录管理暂由技术诊断页承载，扫描结果会自动回到这里。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FeatureLine(title: String, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun DemoAlbumsScreen(
    albums: List<LibraryAlbum>,
    bottomPadding: Dp,
    gridState: LazyGridState,
    layoutMode: AlbumLayoutMode,
    animationStyle: AlbumLayoutAnimationStyle,
    onLayoutModeSelected: (AlbumLayoutMode) -> Unit,
    onAlbumClick: (String) -> Unit,
) {
    val currentLayoutMode = rememberUpdatedState(layoutMode)
    val currentOnLayoutModeSelected = rememberUpdatedState(onLayoutModeSelected)
    var renderedLayoutMode by remember { mutableStateOf(layoutMode) }
    val contentAlpha = remember { Animatable(1f) }
    LaunchedEffect(layoutMode, animationStyle) {
        when (animationStyle) {
            AlbumLayoutAnimationStyle.Fade -> {
                if (renderedLayoutMode != layoutMode) {
                    contentAlpha.animateTo(0f, tween(durationMillis = 90))
                    renderedLayoutMode = layoutMode
                    contentAlpha.snapTo(0f)
                    contentAlpha.animateTo(1f, tween(durationMillis = 150))
                } else {
                    contentAlpha.animateTo(1f, tween(durationMillis = 120))
                }
            }
            AlbumLayoutAnimationStyle.Reflow -> {
                contentAlpha.snapTo(1f)
                renderedLayoutMode = layoutMode
            }
        }
    }
    val columnCount = renderedLayoutMode.gridColumns ?: 1
    val horizontalSpacing = when (columnCount) {
        1 -> 0.dp
        2 -> 14.dp
        else -> 10.dp
    }
    val verticalSpacing = if (renderedLayoutMode.isGrid) 22.dp else 0.dp
    LazyVerticalGrid(
        columns = GridCells.Fixed(columnCount),
        state = gridState,
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            awaitEachGesture {
                var gestureMode = currentLayoutMode.value
                var accumulatedZoom = 1f
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.count { it.pressed } >= 2) {
                        accumulatedZoom *= event.calculateZoom()
                        val densitySteps = albumLayoutDensitySteps(accumulatedZoom)
                        if (densitySteps != 0) {
                            val targetMode = gestureMode.shiftDensity(densitySteps)
                            if (targetMode != gestureMode) {
                                gestureMode = targetMode
                                currentOnLayoutModeSelected.value(targetMode)
                            }
                            accumulatedZoom = 1f
                        }
                        event.changes.forEach { it.consume() }
                    }
                    if (event.changes.none { it.pressed }) break
                }
            }
        },
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
    ) {
        item(key = "albums-title", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = "专辑",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        items(albums, key = { it.albumId.toString() }) { album ->
            AlbumMorphingItem(
                album = album,
                layoutMode = renderedLayoutMode,
                animateReflow = animationStyle == AlbumLayoutAnimationStyle.Reflow,
                onAlbumClick = onAlbumClick,
                modifier = Modifier.alpha(contentAlpha.value).animateItem(
                    fadeInSpec = null,
                    placementSpec = if (animationStyle == AlbumLayoutAnimationStyle.Reflow) {
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        )
                    } else {
                        null
                    },
                    fadeOutSpec = null,
                ),
            )
        }
    }
}

private const val AlbumLayoutZoomStep = 1.18

internal fun albumLayoutDensitySteps(zoom: Float): Int {
    if (!zoom.isFinite() || zoom <= 0f) return 0
    val rawSteps = -ln(zoom.toDouble()) / ln(AlbumLayoutZoomStep)
    return when {
        rawSteps >= 1.0 -> floor(rawSteps).toInt()
        rawSteps <= -1.0 -> ceil(rawSteps).toInt()
        else -> 0
    }
}

@Composable
private fun AlbumMorphingItem(
    album: LibraryAlbum,
    layoutMode: AlbumLayoutMode,
    animateReflow: Boolean,
    onAlbumClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().clipToBounds().clickable { onAlbumClick(album.albumId.toString()) },
    ) {
        val isGrid = layoutMode.isGrid
        val isCoverless = layoutMode == AlbumLayoutMode.Coverless
        val layoutAnimation = if (animateReflow) {
            spring<Dp>(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
        } else {
            snap()
        }
        val artworkSize by animateDpAsState(
            targetValue = when {
                isGrid -> maxWidth
                isCoverless -> 0.dp
                else -> 56.dp
            },
            animationSpec = layoutAnimation,
            label = "album-artwork-size",
        )
        val rowProgress by animateFloatAsState(
            targetValue = if (isGrid) 0f else 1f,
            animationSpec = if (animateReflow) {
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                )
            } else {
                snap()
            },
            label = "album-row-progress",
        )
        val gap by animateDpAsState(
            targetValue = when {
                isGrid && (layoutMode.gridColumns ?: 1) >= 4 -> 6.dp
                isGrid -> 9.dp
                isCoverless -> 0.dp
                else -> 12.dp
            },
            animationSpec = layoutAnimation,
            label = "album-content-gap",
        )
        val verticalPadding by animateDpAsState(
            targetValue = when {
                isGrid -> 0.dp
                isCoverless -> 10.dp
                else -> 7.dp
            },
            animationSpec = layoutAnimation,
            label = "album-vertical-padding",
        )

        Layout(
            content = {
                LibraryArtwork(
                    artworkSource = album.artworkSource,
                    identity = album.albumId.toString(),
                    cornerRadius = if (isGrid) 18 else 10,
                )
                AlbumText(
                    album = album,
                    compact = isGrid && (layoutMode.gridColumns ?: 1) >= 4,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val artworkSizePx = artworkSize.roundToPx().coerceIn(0, width)
            val gapPx = gap.roundToPx().coerceAtLeast(0)
            val paddingPx = verticalPadding.roundToPx().coerceAtLeast(0)
            val progress = rowProgress.coerceIn(0f, 1f)
            val rowTextWidth = (width - artworkSizePx - gapPx).coerceAtLeast(0)
            val textWidth = lerpInt(width, rowTextWidth, progress).coerceAtLeast(0)
            val artworkPlaceable = measurables[0].measure(
                Constraints.fixed(artworkSizePx, artworkSizePx),
            )
            val textPlaceable = measurables[1].measure(
                Constraints(maxWidth = textWidth),
            )

            val gridHeight = artworkSizePx + gapPx + textPlaceable.height
            val rowHeight = max(artworkSizePx, textPlaceable.height) + paddingPx * 2
            val height = lerpInt(gridHeight, rowHeight, progress).coerceAtLeast(0)
            val textX = lerpInt(0, artworkSizePx + gapPx, progress)
            val centeredTextY = ((rowHeight - textPlaceable.height) / 2).coerceAtLeast(0)
            val textY = lerpInt(artworkSizePx + gapPx, centeredTextY, progress)
            val artworkY = lerpInt(0, paddingPx, progress)

            layout(width, height) {
                artworkPlaceable.placeRelative(0, artworkY)
                textPlaceable.placeRelative(textX, textY)
            }
        }
    }
}

@Composable
private fun AlbumText(album: LibraryAlbum, compact: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            album.title,
            style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            album.albumArtistLabel,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun lerpInt(start: Int, end: Int, fraction: Float): Int =
    (start + (end - start) * fraction).roundToInt()

internal fun shouldShowPinnedLibraryContentTitle(firstVisibleItemIndex: Int): Boolean =
    firstVisibleItemIndex > 0

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
fun DemoTracksScreen(
    tracks: List<LibraryTrack>,
    albumsById: Map<String, LibraryAlbum>,
    bottomPadding: Dp,
    listState: LazyListState,
    currentTrackId: String?,
    onTrackClick: (LibraryTrack) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding),
    ) {
        item(key = "tracks-title") {
            Text(
                text = "歌曲",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 18.dp, top = 8.dp, end = 18.dp, bottom = 2.dp),
            )
        }
        itemsIndexed(tracks, key = { _, track -> track.trackId.toString() }) { index, track ->
            val album = track.albumId?.toString()?.let(albumsById::get)
            val rowColor = if (index % 2 == 0) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
            val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable(enabled = track.isPlayable) { onTrackClick(track) }
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

internal fun formatSongArtistCredits(artists: List<String>): String = when (artists.size) {
    0 -> "未知艺术家"
    1 -> artists.single()
    else -> artists.dropLast(1).joinToString(", ") + " & " + artists.last()
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

@Composable
fun DemoArtistsScreen(artists: List<Pair<String, Int>>, bottomPadding: Dp) {
    LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = bottomPadding)) {
        item {
            Text("艺术家", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(14.dp))
        }
        items(artists, key = { it.first }) { (artist, trackCount) ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(58.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(artist.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(artist, style = MaterialTheme.typography.titleMedium)
                    Text("$trackCount 首曲目", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun DemoPlaylistsScreen(bottomPadding: Dp) {
    Column(
        modifier = Modifier.fillMaxSize().padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("歌单", style = MaterialTheme.typography.headlineMedium)
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(66.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.padding(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("创建第一张歌单", style = MaterialTheme.typography.titleMedium)
                    Text("歌单数据模型将在后续阶段接入", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

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
            IconButton(onClick = {}) { Icon(Icons.Rounded.Lyrics, contentDescription = "歌词") }
        }
        Spacer(Modifier.height(20.dp))
        LibraryArtwork(track.artworkSource, album?.albumId?.toString() ?: track.trackId.toString(), Modifier.fillMaxWidth().aspectRatio(1f), cornerRadius = 26)
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
        TextButton(onClick = {}) {
            Icon(Icons.Rounded.Lyrics, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("查看歌词")
        }
        }
    }
}

@Composable
internal fun PlaybackModeSheet(
    selectedMode: PlaybackMode,
    onDismiss: () -> Unit,
    onModeSelected: (PlaybackMode) -> Unit,
    backdrop: Backdrop,
    glassStyle: DemoGlassStyle,
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
private fun RoundedPlaybackModeIcon(
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

internal fun formatDuration(durationMs: Long?): String {
    if (durationMs == null || durationMs < 0) return "—:—"
    val totalSeconds = durationMs / 1_000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
