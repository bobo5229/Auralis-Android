package com.bobo.auralis.mobile.ui.app

import com.bobo.auralis.mobile.ui.demo.*

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bobo.auralis.mobile.ui.library.LibraryManagementActivity
import com.bobo.auralis.mobile.ui.theme.GlassStyle
import com.bobo.auralis.mobile.ui.theme.AuralisThemeMode
import com.bobo.auralis.mobile.ui.theme.AlbumLayoutAnimationStyle
import com.bobo.auralis.mobile.ui.theme.AlbumLayoutMode

@Composable
internal fun AuralisDemoApp(
    glassStyle: GlassStyle,
    onGlassStyleSelected: (GlassStyle) -> Unit,
    liquidGlassBlurDp: Float,
    onLiquidGlassBlurDpChanged: (Float) -> Unit,
    themeMode: AuralisThemeMode,
    albumLayoutMode: AlbumLayoutMode,
    albumLayoutAnimationStyle: AlbumLayoutAnimationStyle,
    onThemeModeSelected: (AuralisThemeMode) -> Unit,
    onAlbumLayoutModeSelected: (AlbumLayoutMode) -> Unit,
    onAlbumLayoutAnimationStyleSelected: (AlbumLayoutAnimationStyle) -> Unit,
    viewModel: LibraryViewModel = viewModel(),
) {
    val context = LocalContext.current
    val library by viewModel.library.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    when {
        library == null && playback.currentTrack == null -> LibraryLoadingScreen()
        library?.tracks.isNullOrEmpty() && playback.currentTrack == null -> DemoWelcomeScreen(
            onManageLibrary = { context.startActivity(Intent(context, LibraryManagementActivity::class.java)) },
        )
        else -> DemoProductShell(
            library = library ?: com.bobo.auralis.mobile.library.browser.LibrarySnapshot(),
            playback = playback,
            onPlay = viewModel::play,
            onTogglePlayback = viewModel::togglePlayPause,
            onNext = viewModel::playNext,
            onPrevious = viewModel::playPrevious,
            onSeek = viewModel::seekTo,
            playbackMode = playback.playbackMode,
            onPlaybackModeSelected = viewModel::changePlaybackMode,
            glassStyle = glassStyle,
            onGlassStyleSelected = onGlassStyleSelected,
            liquidGlassBlurDp = liquidGlassBlurDp,
            onLiquidGlassBlurDpChanged = onLiquidGlassBlurDpChanged,
            themeMode = themeMode,
            onThemeModeSelected = onThemeModeSelected,
            albumLayoutMode = albumLayoutMode,
            onAlbumLayoutModeSelected = onAlbumLayoutModeSelected,
            albumLayoutAnimationStyle = albumLayoutAnimationStyle,
            onAlbumLayoutAnimationStyleSelected = onAlbumLayoutAnimationStyleSelected,
        )
    }
}
