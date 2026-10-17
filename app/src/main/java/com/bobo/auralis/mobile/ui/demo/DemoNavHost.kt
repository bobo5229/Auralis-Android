package com.bobo.auralis.mobile.ui.demo

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.bobo.auralis.mobile.library.browser.LibrarySnapshot
import com.bobo.auralis.mobile.library.browser.LibraryTrack
import com.bobo.auralis.mobile.library.model.TrackId
import com.bobo.auralis.mobile.playback.PlaybackConnectionState
import com.bobo.auralis.mobile.playback.PlaybackMode
import com.bobo.auralis.mobile.ui.theme.AlbumLayoutMode
import com.bobo.auralis.mobile.ui.theme.AlbumLayoutAnimationStyle

/** Connects destinations to page data and callbacks; owns no playback or scroll state. */
@Composable
internal fun DemoNavHost(
    navigation: DemoNavigationState,
    pages: LibraryPageState,
    library: LibrarySnapshot,
    playback: PlaybackConnectionState,
    currentTrack: LibraryTrack?,
    contentBottomPadding: Dp,
    albumLayoutMode: AlbumLayoutMode,
    albumLayoutAnimationStyle: AlbumLayoutAnimationStyle,
    onAlbumLayoutModeSelected: (AlbumLayoutMode) -> Unit,
    onPlay: (TrackId, List<LibraryTrack>) -> Unit,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    playbackMode: PlaybackMode,
    onPlaybackModeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val albumsById = library.albums.associateBy { it.albumId.toString() }
    NavHost(
        navController = navigation.controller,
        startDestination = "home",
        modifier = modifier,
    ) {
        composable("home") { DemoPlaceholderScreen("首页", contentBottomPadding) }
        composable("library") {
            DemoLibraryScreen(
                bottomPadding = contentBottomPadding,
                onAlbumsClick = { navigation.openLibrarySection("library/albums") },
                onTracksClick = { navigation.openLibrarySection("library/tracks") },
                onPlaylistsClick = { navigation.openLibrarySection("library/playlists") },
                onGenresClick = { navigation.openLibrarySection("library/genres") },
            )
        }
        composable("library/albums") {
            DemoAlbumsScreen(
                albums = library.albums,
                bottomPadding = contentBottomPadding,
                gridState = pages.albumsGridState,
                layoutMode = albumLayoutMode,
                animationStyle = albumLayoutAnimationStyle,
                onLayoutModeSelected = onAlbumLayoutModeSelected,
                onAlbumClick = { navigation.openAlbum(it.toString()) },
            )
        }
        composable("library/tracks") {
            DemoTracksScreen(
                tracks = library.tracks,
                albumsById = albumsById,
                bottomPadding = contentBottomPadding,
                listState = pages.tracksListState,
                currentTrackId = playback.currentMediaId,
                onTrackClick = { track, queue -> onPlay(track.trackId, queue) },
            )
        }
        composable("library/playlists") { DemoPlaylistsScreen(contentBottomPadding) }
        composable("library/genres") {
            DemoGenresScreen(library.genres, contentBottomPadding, pages.genresListState) {
                navigation.openGenre(it.genreId.toString())
            }
        }
        composable("library/genres/{genreId}") { entry ->
            val genre = library.genres.firstOrNull { it.genreId.toString() == entry.arguments?.getString("genreId") }
            if (genre == null) {
                DemoPlaceholderScreen("该流派暂无歌曲", contentBottomPadding)
            } else {
                DemoTracksScreen(
                    tracks = genre.tracks,
                    albumsById = albumsById,
                    bottomPadding = contentBottomPadding,
                    listState = pages.genreTracksListState,
                    currentTrackId = playback.currentMediaId,
                    onTrackClick = { track, queue -> onPlay(track.trackId, queue) },
                    title = genre.displayName,
                )
            }
        }
        composable("roam") { DemoPlaceholderScreen("漫游", contentBottomPadding) }
        composable("search") { DemoPlaceholderScreen("搜索", contentBottomPadding) }
        composable("album/{albumId}") { entry ->
            val album = albumsById[entry.arguments?.getString("albumId")]
            if (album != null) {
                DemoAlbumDetailScreen(
                    album = album,
                    bottomPadding = contentBottomPadding,
                    currentTrackId = playback.currentMediaId,
                    onPlayAlbum = { album.tracks.firstOrNull { it.isPlayable }?.let { onPlay(it.trackId, album.tracks) } },
                    onTrackClick = { onPlay(it.trackId, album.tracks) },
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
                    onBack = navigation::back,
                    onToggle = onTogglePlayback,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onSeek = onSeek,
                    playbackMode = playbackMode,
                    onPlaybackModeClick = onPlaybackModeClick,
                )
            }
        }
    }
}
