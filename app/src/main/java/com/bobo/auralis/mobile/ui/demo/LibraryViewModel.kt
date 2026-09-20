package com.bobo.auralis.mobile.ui.demo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bobo.auralis.mobile.library.browser.LibraryBrowserRepository
import com.bobo.auralis.mobile.library.browser.LibrarySnapshot
import com.bobo.auralis.mobile.library.db.AuralisDatabase
import com.bobo.auralis.mobile.library.model.TrackId
import com.bobo.auralis.mobile.playback.PlaybackControllerConnection
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

    fun play(trackId: TrackId) = playbackConnection.play(trackId)

    fun togglePlayPause() = playbackConnection.togglePlayPause()

    fun seekTo(positionMs: Long) = playbackConnection.seekTo(positionMs)

    fun playNext(tracks: List<TrackId>) {
        if (tracks.isEmpty()) return
        val current = playback.value.currentMediaId
        val currentIndex = tracks.indexOfFirst { it.toString() == current }
        val next = tracks[(currentIndex + 1).mod(tracks.size)]
        playbackConnection.play(next)
    }

    fun playPrevious(tracks: List<TrackId>) {
        if (tracks.isEmpty()) return
        val current = playback.value.currentMediaId
        val currentIndex = tracks.indexOfFirst { it.toString() == current }.let { if (it < 0) 0 else it }
        val previous = tracks[Math.floorMod(currentIndex - 1, tracks.size)]
        playbackConnection.play(previous)
    }

    override fun onCleared() {
        playbackConnection.release()
        super.onCleared()
    }
}
