package com.bobo.auralis.mobile.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class PlaybackMediaItemResolverTest {

    @Test
    fun `valid UUID media id becomes TrackId`() {
        val uuid = UUID.fromString("00000000-0000-0000-0000-000000000123")

        assertEquals(uuid, uuid.toString().toTrackId()?.value)
    }

    @Test
    fun `invalid media id is rejected`() {
        assertNull("not-a-track-id".toTrackId())
        assertNull("".toTrackId())
    }
}
