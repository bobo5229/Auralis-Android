package com.bobo.auralis.mobile.ui.demo

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bobo.auralis.mobile.debug.DebugActivity
import com.bobo.auralis.mobile.R
import com.bobo.auralis.mobile.library.browser.LibrarySnapshot
import com.bobo.auralis.mobile.playback.PlaybackConnectionState
import com.bobo.auralis.mobile.playback.PlaybackMode
import com.bobo.auralis.mobile.ui.theme.AuralisThemeMode
import com.bobo.auralis.mobile.ui.theme.AlbumLayoutAnimationStyle
import com.bobo.auralis.mobile.ui.theme.AlbumLayoutMode
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

internal data class DemoDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val mainDestinations = listOf(
    DemoDestination("home", "首页", Icons.Rounded.Home),
    DemoDestination("library", "资料库", Icons.Rounded.LibraryMusic),
    DemoDestination("roam", "漫游", Icons.Rounded.Explore),
    DemoDestination("search", "搜索", Icons.Rounded.Search),
)

private val libraryTitleFontFamily = FontFamily(
    Font(R.font.noto_serif_sc_black_subset, weight = FontWeight.Black),
)

@Composable
internal fun AuralisDemoApp(
    themeMode: AuralisThemeMode,
    albumLayoutMode: AlbumLayoutMode,
    albumLayoutAnimationStyle: AlbumLayoutAnimationStyle,
    playbackMode: PlaybackMode,
    onThemeModeSelected: (AuralisThemeMode) -> Unit,
    onAlbumLayoutModeSelected: (AlbumLayoutMode) -> Unit,
    onAlbumLayoutAnimationStyleSelected: (AlbumLayoutAnimationStyle) -> Unit,
    onPlaybackModeSelected: (PlaybackMode) -> Unit,
    viewModel: LibraryViewModel = viewModel(),
) {
    val context = LocalContext.current
    val library by viewModel.library.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    LaunchedEffect(playbackMode, playback.isConnected) {
        if (playback.isConnected) viewModel.restorePlaybackMode(playbackMode)
    }
    when {
        library == null -> LibraryLoadingScreen()
        library?.tracks.isNullOrEmpty() -> DemoWelcomeScreen(
            onManageLibrary = { context.startActivity(Intent(context, DebugActivity::class.java)) },
        )
        else -> DemoProductShell(
            library = requireNotNull(library),
            playback = playback,
            onPlay = viewModel::play,
            onTogglePlayback = viewModel::togglePlayPause,
            onNext = viewModel::playNext,
            onPrevious = viewModel::playPrevious,
            onSeek = viewModel::seekTo,
            playbackMode = playbackMode,
            onPlaybackModeSelected = { selectedMode ->
                viewModel.changePlaybackMode(selectedMode)
                onPlaybackModeSelected(selectedMode)
            },
            themeMode = themeMode,
            onThemeModeSelected = onThemeModeSelected,
            albumLayoutMode = albumLayoutMode,
            onAlbumLayoutModeSelected = onAlbumLayoutModeSelected,
            albumLayoutAnimationStyle = albumLayoutAnimationStyle,
            onAlbumLayoutAnimationStyleSelected = onAlbumLayoutAnimationStyleSelected,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoProductShell(
    library: LibrarySnapshot,
    playback: PlaybackConnectionState,
    onPlay: (com.bobo.auralis.mobile.library.model.TrackId) -> Unit,
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
    val navController = rememberNavController()
    val albumsGridState = rememberLazyGridState()
    val tracksListState = rememberLazyListState()
    val showAlbumsTitleInTopBar by remember {
        derivedStateOf { shouldShowPinnedLibraryContentTitle(albumsGridState.firstVisibleItemIndex) }
    }
    val showTracksTitleInTopBar by remember {
        derivedStateOf { shouldShowPinnedLibraryContentTitle(tracksListState.firstVisibleItemIndex) }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "home"
    var glassStyle by rememberSaveable { mutableStateOf(DemoGlassStyle.Liquid) }
    var isPlayerCollapsed by remember { mutableStateOf(false) }
    var isPlayerDragActive by remember { mutableStateOf(false) }
    var showPlaybackModeSheet by remember { mutableStateOf(false) }

    val currentTrack = library.tracks.firstOrNull { it.trackId.toString() == playback.currentMediaId }
    val albumsById = library.albums.associateBy { it.albumId.toString() }
    val isMainDestination = mainDestinations.any { it.route == currentRoute }
    val isPlayer = currentRoute == "player"
    LaunchedEffect(isPlayer) {
        if (!isPlayer) showPlaybackModeSheet = false
    }
    val selectedDockRoute = when {
        currentRoute.startsWith("album/") -> "library"
        currentRoute.startsWith("library/") -> "library"
        isMainDestination -> currentRoute
        else -> "home"
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
                                    "library/albums" -> showAlbumsTitleInTopBar
                                    "library/tracks" -> showTracksTitleInTopBar
                                    else -> false
                                },
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                Text(if (currentRoute == "library/tracks") "歌曲" else "专辑")
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
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
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.padding(padding),
            ) {
                composable("home") { DemoPlaceholderScreen("首页", contentBottomPadding) }
                composable("library") {
                    DemoLibraryScreen(
                        bottomPadding = contentBottomPadding,
                        onAlbumsClick = { navController.navigate("library/albums") },
                        onTracksClick = { navController.navigate("library/tracks") },
                        onPlaylistsClick = { navController.navigate("library/playlists") },
                    )
                }
                composable("library/albums") {
                    DemoAlbumsScreen(
                        albums = library.albums,
                        bottomPadding = contentBottomPadding,
                        gridState = albumsGridState,
                        layoutMode = albumLayoutMode,
                        animationStyle = albumLayoutAnimationStyle,
                        onLayoutModeSelected = onAlbumLayoutModeSelected,
                        onAlbumClick = { navController.navigate("album/$it") },
                    )
                }
                composable("library/tracks") {
                    DemoTracksScreen(
                        tracks = library.tracks,
                        albumsById = albumsById,
                        bottomPadding = contentBottomPadding,
                        listState = tracksListState,
                        currentTrackId = playback.currentMediaId,
                        onTrackClick = { onPlay(it.trackId) },
                    )
                }
                composable("library/playlists") { DemoPlaylistsScreen(contentBottomPadding) }
                composable("roam") { DemoPlaceholderScreen("漫游", contentBottomPadding) }
                composable("search") { DemoPlaceholderScreen("搜索", contentBottomPadding) }
                composable("album/{albumId}") { entry ->
                    val album = albumsById[entry.arguments?.getString("albumId")]
                    if (album != null) {
                        DemoAlbumDetailScreen(
                            album = album,
                            bottomPadding = contentBottomPadding,
                            currentTrackId = playback.currentMediaId,
                            onPlayAlbum = { album.tracks.firstOrNull { it.isPlayable }?.let { onPlay(it.trackId) } },
                            onTrackClick = { onPlay(it.trackId) },
                        )
                    }
                }
                composable("player") {
                    currentTrack?.let { track ->
                        DemoPlayerScreen(
                            album = track.albumId?.toString()?.let(albumsById::get),
                            track = track,
                            isPlaying = playback.isPlaying,
                            positionMs = playback.positionMs,
                            durationMs = playback.durationMs.takeIf { it > 0 } ?: track.durationMs,
                            onBack = { navController.popBackStack() },
                            onToggle = onTogglePlayback,
                            onPrevious = onPrevious,
                            onNext = onNext,
                            onSeek = onSeek,
                            playbackMode = playbackMode,
                            onPlaybackModeClick = { showPlaybackModeSheet = true },
                        )
                    }
                }
            }
        }

        if (!isPlayer) {
            DemoFloatingDock(
                backdrop = backdrop,
                glassStyle = glassStyle,
                destinations = mainDestinations,
                selectedRoute = selectedDockRoute,
                currentTrack = currentTrack,
                isPlaying = playback.isPlaying,
                positionMs = playback.positionMs,
                durationMs = playback.durationMs.takeIf { it > 0 } ?: currentTrack?.durationMs,
                isPlayerCollapsed = isPlayerCollapsed,
                onDestinationSelected = { route ->
                    navController.navigate(route) {
                        popUpTo("home") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onTogglePlayback = onTogglePlayback,
                onPrevious = onPrevious,
                onNext = onNext,
                onOpenPlayer = { if (currentTrack != null) navController.navigate("player") },
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

@Composable
private fun AlbumsLayoutMenu(
    selectedMode: AlbumLayoutMode,
    onModeSelected: (AlbumLayoutMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.ViewModule, contentDescription = "切换专辑布局")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text(
                text = "封面网格",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            AlbumLayoutMode.entries.forEach { mode ->
                if (mode == AlbumLayoutMode.List) {
                    Text(
                        text = "其他布局",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    leadingIcon = {
                        if (mode == selectedMode) {
                            Icon(Icons.Rounded.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        expanded = false
                        onModeSelected(mode)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoTopBar(
    title: String,
    useLibraryTitleFont: Boolean,
    glassStyle: DemoGlassStyle,
    onGlassStyleSelected: (DemoGlassStyle) -> Unit,
    themeMode: AuralisThemeMode,
    onThemeModeSelected: (AuralisThemeMode) -> Unit,
    albumLayoutAnimationStyle: AlbumLayoutAnimationStyle,
    onAlbumLayoutAnimationStyleSelected: (AlbumLayoutAnimationStyle) -> Unit,
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Text(
                title,
                style = if (useLibraryTitleFont) {
                    MaterialTheme.typography.titleLarge.copy(
                        fontFamily = libraryTitleFontFamily,
                        fontWeight = FontWeight.Black,
                    )
                } else {
                    MaterialTheme.typography.titleLarge
                },
            )
        },
        actions = {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "更多")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                Text(
                    text = "外观模式",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                AuralisThemeMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label) },
                        leadingIcon = { if (mode == themeMode) Icon(Icons.Rounded.Check, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onThemeModeSelected(mode)
                        },
                    )
                }
                Text(
                    text = "玻璃效果",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                DemoGlassStyle.entries.forEach { style ->
                    DropdownMenuItem(
                        text = { Text(style.label) },
                        leadingIcon = { if (style == glassStyle) Icon(Icons.Rounded.Check, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onGlassStyleSelected(style)
                        },
                    )
                }
                Text(
                    text = "专辑布局动效",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                AlbumLayoutAnimationStyle.entries.forEach { style ->
                    DropdownMenuItem(
                        text = { Text(style.label) },
                        leadingIcon = {
                            if (style == albumLayoutAnimationStyle) {
                                Icon(Icons.Rounded.Check, contentDescription = null)
                            }
                        },
                        onClick = {
                            menuExpanded = false
                            onAlbumLayoutAnimationStyleSelected(style)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("技术诊断") },
                    onClick = {
                        menuExpanded = false
                        context.startActivity(Intent(context, DebugActivity::class.java))
                    },
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}
