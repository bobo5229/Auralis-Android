package com.bobo.auralis.mobile.playback

import androidx.media3.common.Player

/** Keep control icons and actions consistent while buffering or awaiting audio focus. */
internal val Player.showPauseButton: Boolean
    get() = playWhenReady && playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED

internal fun Player.togglePlayback() {
    if (showPauseButton) {
        pause()
    } else {
        when (playbackState) {
            Player.STATE_IDLE -> prepare()
            Player.STATE_ENDED -> seekToDefaultPosition()
        }
        play()
    }
}
