package com.bobo.auralis.mobile.ui.app

import com.bobo.auralis.mobile.library.model.*
import com.bobo.auralis.mobile.playback.PlaybackTrack
import org.junit.Assert.*
import org.junit.Test

class PlaybackPresentationTest {
    @Test fun playerUsesTheQueuesExactSourceAndOrderedCredits() {
        val source = LibraryAudioSource(TrackSourceId.random(), "content://test/queued", "content://test/tree",
            "Music/song.flac", "song.flac", 10, 100, 20)
        val snapshot = PlaybackTrack(TrackId.random(), "Queued", listOf("Artist B", "Artist A"),
            AlbumId.random(), "Album", 180_000, 2, 3, source, true)
        val track = snapshot.toPlayerTrack()
        assertEquals(snapshot.trackId, track.trackId)
        assertEquals(snapshot.albumId, track.albumId)
        assertEquals(listOf("Artist B", "Artist A"), track.artists)
        assertSame(source, track.audioSource)
        assertEquals(source, track.artworkSource!!.audioSource)
        assertTrue(track.artworkSource!!.hasEmbeddedArtwork)
        assertEquals(2, track.discNumber)
        assertEquals(3, track.trackNumber)
    }
}
