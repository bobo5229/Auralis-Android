package com.bobo.auralis.mobile.ui.demo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bobo.auralis.mobile.library.browser.LibraryBrowserRepository
import com.bobo.auralis.mobile.library.browser.LibrarySnapshot
import com.bobo.auralis.mobile.library.browser.LibraryTrack
import com.bobo.auralis.mobile.library.db.AuralisDatabase
import com.bobo.auralis.mobile.library.model.TrackId
import com.bobo.auralis.mobile.playback.PlaybackControllerConnection
import com.bobo.auralis.mobile.playback.PlaybackMode
import com.bobo.auralis.mobile.playback.PlaybackQueueTrack
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

internal class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val playbackConnection = PlaybackControllerConnection(application)
    private val repository = LibraryBrowserRepository(AuralisDatabase.get(application))

    val library: StateFlow<LibrarySnapshot?> = repository.snapshot.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )
    val playback = playbackConnection.state

    fun play(trackId: TrackId, tracks: List<LibraryTrack>) {
        playbackConnection.play(trackId, playablePageQueue(tracks))
    }

    fun changePlaybackMode(mode: PlaybackMode) =
        playbackConnection.changePlaybackMode(mode)

    fun togglePlayPause() = playbackConnection.togglePlayPause()

    fun seekTo(positionMs: Long) = playbackConnection.seekTo(positionMs)

    fun playNext() = playbackConnection.skipToNext()

    fun playPrevious() = playbackConnection.skipToPrevious()

    override fun onCleared() {
        playbackConnection.release()
        super.onCleared()
    }
}

internal fun playablePageQueue(tracks: List<LibraryTrack>): List<PlaybackQueueTrack> = tracks
    .filter { it.isPlayable }
    .map { PlaybackQueueTrack(it.trackId, it.albumId?.toString(), it.discNumber, it.trackNumber) }
