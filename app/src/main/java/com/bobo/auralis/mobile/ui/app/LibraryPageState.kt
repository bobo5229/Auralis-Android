package com.bobo.auralis.mobile.ui.app

import com.bobo.auralis.mobile.ui.demo.*

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable

/** Shared by pages and the shell title; remains outside destination composition. */
internal class LibraryPageState(
    val albumsGridState: LazyGridState,
    val tracksListState: LazyListState,
    val genresListState: LazyListState,
    val genreTracksListState: LazyListState,
) {
    val showAlbumsTitleInTopBar by derivedStateOf {
        shouldShowPinnedLibraryContentTitle(albumsGridState.firstVisibleItemIndex)
    }
    val showTracksTitleInTopBar by derivedStateOf {
        shouldShowPinnedLibraryContentTitle(tracksListState.firstVisibleItemIndex)
    }
    val showGenresTitleInTopBar by derivedStateOf {
        shouldShowPinnedLibraryContentTitle(genresListState.firstVisibleItemIndex)
    }
    val showGenreTracksTitleInTopBar by derivedStateOf {
        shouldShowPinnedLibraryContentTitle(genreTracksListState.firstVisibleItemIndex)
    }
}

@Composable
internal fun rememberLibraryPageState(genreId: String?): LibraryPageState {
    val albums = rememberLazyGridState()
    val tracks = rememberLazyListState()
    val genres = rememberLazyListState()
    // Preserve the existing reset on genre argument changes, including leaving the detail route.
    val genreTracks = rememberSaveable(genreId, saver = LazyListState.Saver) { LazyListState() }
    return remember(albums, tracks, genres, genreTracks) {
        LibraryPageState(albums, tracks, genres, genreTracks)
    }
}
