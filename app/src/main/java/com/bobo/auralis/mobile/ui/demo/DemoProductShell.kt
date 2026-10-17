package com.bobo.auralis.mobile.ui.demo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import com.bobo.auralis.mobile.library.browser.LibrarySnapshot
import com.bobo.auralis.mobile.playback.PlaybackConnectionState
import com.bobo.auralis.mobile.playback.PlaybackMode
import com.bobo.auralis.mobile.ui.theme.AuralisThemeMode
import com.bobo.auralis.mobile.ui.theme.AlbumLayoutAnimationStyle
import com.bobo.auralis.mobile.ui.theme.AlbumLayoutMode
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DemoProductShell(
    library: LibrarySnapshot,
    playback: PlaybackConnectionState,
    onPlay: (com.bobo.auralis.mobile.library.model.TrackId, List<com.bobo.auralis.mobile.library.browser.LibraryTrack>) -> Unit,
    onTogglePlayback: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    playbackMode: PlaybackMode,
    onPlaybackModeSelected: (PlaybackMode) -> Unit,
    themeMode: AuralisThemeMode,
    onThemeModeSelected: (AuralisThemeMode) -> Unit,
    albumLayoutMode: AlbumLayoutMode,
    onAlbumLayoutModeSelected: (AlbumLayoutMode) -> Unit,
    albumLayoutAnimationStyle: AlbumLayoutAnimationStyle,
    onAlbumLayoutAnimationStyleSelected: (AlbumLayoutAnimationStyle) -> Unit,
) {
    val navigation = rememberDemoNavigationState()
    val pages = rememberLibraryPageState(navigation.genreId)
    val currentRoute = navigation.currentRoute
    val currentGenre = library.genres.firstOrNull { it.genreId.toString() == navigation.genreId }
    var glassStyle by rememberSaveable { mutableStateOf(DemoGlassStyle.Liquid) }
    var isPlayerCollapsed by remember { mutableStateOf(false) }
    var isPlayerDragActive by remember { mutableStateOf(false) }
    var showPlaybackModeSheet by remember { mutableStateOf(false) }

    val currentTrack = library.tracks.firstOrNull { it.trackId.toString() == playback.currentMediaId }
    val isMainDestination = navigation.isMainDestination
    val isPlayer = navigation.isPlayer
    LaunchedEffect(isPlayer) {
        if (!isPlayer) showPlaybackModeSheet = false
    }
    LaunchedEffect(currentTrack == null) {
        if (currentTrack == null) {
            isPlayerCollapsed = false
            isPlayerDragActive = false
        }
    }
    val contentBottomPadding by animateDpAsState(
        targetValue = if (currentTrack == null || isPlayerCollapsed) 94.dp else 170.dp,
        label = "player-bottom-content-padding",
    )
    val backgroundColor = MaterialTheme.colorScheme.background
    val backgroundBlur by animateDpAsState(
        targetValue = if (isPlayerDragActive) 18.dp else 0.dp,
        label = "playbar-drag-background-blur",
    )
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize().blur(backgroundBlur).layerBackdrop(backdrop),
            containerColor = backgroundColor,
            topBar = {
                when {
                    isMainDestination -> DemoTopBar(
                        title = mainDestinations.first { it.route == currentRoute }.label,
                        useLibraryTitleFont = currentRoute == "library",
                        glassStyle = glassStyle,
                        onGlassStyleSelected = { glassStyle = it },
                        themeMode = themeMode,
                        onThemeModeSelected = onThemeModeSelected,
                        albumLayoutAnimationStyle = albumLayoutAnimationStyle,
                        onAlbumLayoutAnimationStyleSelected = onAlbumLayoutAnimationStyleSelected,
                    )
                    !isPlayer -> TopAppBar(
                        title = {
                            AnimatedVisibility(
                                visible = when (currentRoute) {
                                    "library/albums" -> pages.showAlbumsTitleInTopBar
                                    "library/tracks" -> pages.showTracksTitleInTopBar
                                    "library/genres" -> pages.showGenresTitleInTopBar
                                    "library/genres/{genreId}" -> pages.showGenreTracksTitleInTopBar
                                    else -> false
                                },
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                Text(when (currentRoute) {
                                    "library/tracks" -> "歌曲"
                                    "library/genres" -> "流派"
                                    "library/genres/{genreId}" -> currentGenre?.displayName ?: "流派"
                                    else -> "专辑"
                                })
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = navigation::back) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                            }
                        },
                        actions = {
                            if (currentRoute == "library/albums") {
                                AlbumsLayoutMenu(
                                    selectedMode = albumLayoutMode,
                                    onModeSelected = onAlbumLayoutModeSelected,
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    )
                    else -> Unit
                }
            },
        ) { padding ->
            DemoNavHost(
                navigation = navigation,
                pages = pages,
                library = library,
                playback = playback,
                currentTrack = currentTrack,
                contentBottomPadding = contentBottomPadding,
                albumLayoutMode = albumLayoutMode,
                albumLayoutAnimationStyle = albumLayoutAnimationStyle,
                onAlbumLayoutModeSelected = onAlbumLayoutModeSelected,
                onPlay = onPlay,
                onTogglePlayback = onTogglePlayback,
                onPrevious = onPrevious,
                onNext = onNext,
                onSeek = onSeek,
                playbackMode = playbackMode,
                onPlaybackModeClick = { showPlaybackModeSheet = true },
                modifier = Modifier.padding(padding),
            )
        }

        if (!isPlayer) {
            DemoFloatingDock(
                backdrop = backdrop,
                glassStyle = glassStyle,
                destinations = mainDestinations,
                selectedRoute = navigation.selectedDockRoute,
                currentTrack = currentTrack,
                isPlaying = playback.isPlaying,
                positionMs = playback.positionMs,
                durationMs = playback.durationMs.takeIf { it > 0 } ?: currentTrack?.durationMs,
                isPlayerCollapsed = isPlayerCollapsed,
                onDestinationSelected = navigation::selectMainDestination,
                onTogglePlayback = onTogglePlayback,
                onPrevious = onPrevious,
                onNext = onNext,
                onOpenPlayer = { navigation.openPlayer(currentTrack != null) },
                onCollapsePlayer = { isPlayerCollapsed = true },
                onExpandPlayer = { isPlayerCollapsed = false },
                onPlayerInteractionActiveChanged = { isPlayerDragActive = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        if (isPlayer && showPlaybackModeSheet) {
            PlaybackModeSheet(
                selectedMode = playbackMode,
                onDismiss = { showPlaybackModeSheet = false },
                onModeSelected = { selectedMode ->
                    onPlaybackModeSelected(selectedMode)
                    showPlaybackModeSheet = false
                },
                backdrop = backdrop,
                glassStyle = glassStyle,
            )
        }
    }
}
