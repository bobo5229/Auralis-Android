package com.bobo.auralis.mobile.ui.app

import com.bobo.auralis.mobile.library.browser.LibraryTrack
import com.bobo.auralis.mobile.playback.PlaybackTrack

/** Adapt the queue's snapshot for the existing player components, independently of browsing. */
internal fun PlaybackTrack.toPlayerTrack(): LibraryTrack = LibraryTrack(
    trackId = trackId,
    albumId = albumId,
    title = title,
    artists = artists,
    albumTitle = albumTitle,
    durationMs = durationMs,
    discNumber = discNumber,
    trackNumber = trackNumber,
    isPlayable = true,
    unavailableReason = null,
    audioSource = audioSource,
    hasEmbeddedArtwork = hasEmbeddedArtwork,
)
