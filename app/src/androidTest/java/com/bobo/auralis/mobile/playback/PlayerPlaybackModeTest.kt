package com.bobo.auralis.mobile.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerPlaybackModeTest {
    @Test
    fun modeFollowsQueueReplacementTransitionAndClear() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
            try {
                assertEquals(PlaybackMode.Sequential, player.currentPlaybackMode())
                for (mode in PlaybackMode.entries) {
                    player.setMediaItems(listOf(item(mode), item(mode)), 1, 1234L)
                    assertEquals(mode, player.currentPlaybackMode())
                    assertEquals(1, player.currentMediaItemIndex)
                    assertEquals(1234L, player.currentPosition)
                    player.seekToPreviousMediaItem()
                    assertEquals(mode, player.currentPlaybackMode())
                }
                player.clearMediaItems()
                assertEquals(PlaybackMode.Sequential, player.currentPlaybackMode())
                player.setMediaItem(MediaItem.Builder().setMediaId("no-mode").setUri("content://test/no-mode").build())
                assertEquals(PlaybackMode.Sequential, player.currentPlaybackMode())
            } finally {
                player.release()
            }
        }
    }

    private fun item(mode: PlaybackMode): MediaItem = MediaItem.Builder()
        .setMediaId(mode.key)
        .setUri("content://test/${mode.key}")
        .setMediaMetadata(MediaMetadata.Builder().setExtras(Bundle().apply {
            putString(PlaybackModeMetadata.Mode, mode.key)
        }).build())
        .build()
}
