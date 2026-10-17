package com.bobo.auralis.mobile.playback

import androidx.media3.common.Player

/** The current queue is authoritative; preferences are only a new-queue default. */
internal fun Player.currentPlaybackMode(): PlaybackMode = PlaybackMode.fromKey(
    currentMediaItem?.mediaMetadata?.extras?.getString(PlaybackModeMetadata.Mode),
)
