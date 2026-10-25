package com.bobo.auralis.mobile.playback

import com.bobo.auralis.mobile.library.browser.LibraryTrack

internal fun playablePageQueue(tracks: List<LibraryTrack>): List<PlaybackQueueTrack> = tracks
    .filter { it.isPlayable }
    .map { PlaybackQueueTrack(it.trackId, it.albumId?.toString(), it.discNumber, it.trackNumber) }
