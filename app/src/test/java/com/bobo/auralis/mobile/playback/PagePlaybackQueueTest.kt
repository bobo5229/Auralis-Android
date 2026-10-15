package com.bobo.auralis.mobile.playback

import com.bobo.auralis.mobile.library.browser.LibraryTrack
import com.bobo.auralis.mobile.library.model.AlbumId
import com.bobo.auralis.mobile.library.model.TrackId
import com.bobo.auralis.mobile.ui.demo.playablePageQueue
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class PagePlaybackQueueTest {
    private fun track(album: AlbumId, number: Int, playable: Boolean = true) = LibraryTrack(
        TrackId.random(), album, "Song $number", listOf("Artist"), "Album", 1000,
        1, number, playable, null, null,
    )

    @Test fun `album and filtered page queues contain only visible playable songs in page order`() {
        val album = AlbumId.random()
        val a = track(album, 1)
        val bad = track(album, 2, false)
        val b = track(album, 3)
        val other = track(AlbumId.random(), 1)
        val albumQueue = playablePageQueue(listOf(a, bad, b))
        assertEquals(listOf(a.trackId, b.trackId), albumQueue.map { it.trackId })
        val filtered = playablePageQueue(listOf(b, a))
        val queue = buildModePlaybackQueue(b.trackId, filtered, PlaybackMode.Sequential)
        assertEquals(listOf(b.trackId, a.trackId), queue.tracks.map { it.trackId })
        assertFalse(queue.tracks.any { it.trackId == other.trackId })
        assertEquals(0, queue.startIndex)
    }

    @Test fun `switching from shuffle restores original page order without expanding its scope`() {
        val album = AlbumId.random()
        val candidates = playablePageQueue((1..8).map { track(album, it) })
            .mapIndexed { index, track -> track.copy(queueIndex = index) }
        val shuffled = buildModePlaybackQueue(candidates[3].trackId, candidates, PlaybackMode.ShuffleTracks, Random(9))
        val restored = buildModePlaybackQueue(candidates[3].trackId, restoreQueueOrder(shuffled.tracks), PlaybackMode.Sequential)
        assertEquals(candidates, restored.tracks)
        assertEquals(3, restored.startIndex)
    }
}
