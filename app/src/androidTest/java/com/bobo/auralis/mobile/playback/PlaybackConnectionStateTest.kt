package com.bobo.auralis.mobile.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackConnectionStateTest {
    @Test
    fun errorSurvivesRepeatedSnapshotsAndClearsWhenPlayerRecovers() {
        var failure: PlaybackException? = PlaybackException(
            "bad audio", null, PlaybackException.ERROR_CODE_DECODING_FAILED,
        )
        var state = Player.STATE_IDLE
        val player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getPlayerError" -> failure
                "getCurrentMediaItem" -> null
                "isPlaying" -> false
                "getPlaybackState" -> state
                "getPlayWhenReady" -> true
                "getCurrentPosition" -> 0L
                "getDuration" -> androidx.media3.common.C.TIME_UNSET
                else -> error("Unexpected player call: ${method.name}")
            }
        } as Player

        repeat(3) {
            assertEquals("ERROR_CODE_DECODING_FAILED: bad audio", player.connectionState().error)
        }
        assertFalse(player.connectionState().showPauseButton)
        failure = null
        state = Player.STATE_BUFFERING
        val recovered = player.connectionState()
        assertNull(recovered.error)
        assertFalse(recovered.isPlaying)
        assertTrue(recovered.showPauseButton)
    }
}
