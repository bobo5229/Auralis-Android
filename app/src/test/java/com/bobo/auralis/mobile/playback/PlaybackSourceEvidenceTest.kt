package com.bobo.auralis.mobile.playback

import androidx.media3.common.PlaybackException
import com.bobo.auralis.mobile.library.model.TrackId
import com.bobo.auralis.mobile.library.model.TrackSourceId
import org.junit.Assert.*
import org.junit.Test

class PlaybackSourceEvidenceTest {
    @Test fun `evidence is rejected after file replacement or logical reassignment`() {
        val track = TrackId.random()
        val source = TrackSourceId.random()
        val evidence = PlaybackSourceEvidence(track, source, 100, 200)
        assertTrue(evidence.matches(track, source, 100, 200))
        assertFalse(evidence.matches(track, TrackSourceId.random(), 100, 200))
        assertFalse(evidence.matches(TrackId.random(), source, 100, 200))
        assertFalse(evidence.matches(track, source, 101, 200))
        assertFalse(evidence.matches(track, source, 100, 201))
    }

    @Test fun `temporary access errors do not permanently blacklist a file`() {
        assertFalse(isPermanentPlaybackFailure(PlaybackException.ERROR_CODE_IO_NO_PERMISSION))
        assertFalse(isPermanentPlaybackFailure(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))
        assertFalse(isPermanentPlaybackFailure(PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
        assertTrue(isPermanentPlaybackFailure(PlaybackException.ERROR_CODE_DECODING_FAILED))
        assertTrue(isPermanentPlaybackFailure(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED))
    }
}
