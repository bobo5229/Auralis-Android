package com.bobo.auralis.mobile.playback

import androidx.media3.common.Player
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerPlaybackControlsTest {
    @Test
    fun `buffering playback can be paused before audio starts`() {
        assertEquals(listOf("pause"), toggle(Player.STATE_BUFFERING, playWhenReady = true))
    }

    @Test
    fun `audio focus suppressed playback can be paused`() {
        assertEquals(listOf("pause"), toggle(Player.STATE_READY, playWhenReady = true))
    }

    @Test
    fun `paused playback resumes without resetting its position`() {
        assertEquals(listOf("play"), toggle(Player.STATE_READY, playWhenReady = false))
        assertEquals(listOf("play"), toggle(Player.STATE_BUFFERING, playWhenReady = false))
    }

    @Test
    fun `stopped or failed playback prepares before resuming`() {
        for (playWhenReady in listOf(false, true)) {
            assertEquals(listOf("prepare", "play"), toggle(Player.STATE_IDLE, playWhenReady))
        }
    }

    @Test
    fun `ended playback seeks to the default position before replaying`() {
        for (playWhenReady in listOf(false, true)) {
            assertEquals(listOf("seekToDefaultPosition", "play"), toggle(Player.STATE_ENDED, playWhenReady))
        }
    }

    private fun toggle(state: Int, playWhenReady: Boolean): List<String> {
        val commands = mutableListOf<String>()
        val player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getPlaybackState" -> state
                "getPlayWhenReady" -> playWhenReady
                "isPlaying" -> false
                "pause", "prepare", "seekToDefaultPosition", "play" -> {
                    commands += method.name
                    null
                }
                else -> error("Unexpected player call: ${method.name}")
            }
        } as Player
        val showPause = player.showPauseButton
        player.togglePlayback()
        assertEquals(commands == listOf("pause"), showPause)
        return commands
    }
}
