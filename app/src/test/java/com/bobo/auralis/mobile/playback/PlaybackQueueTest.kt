package com.bobo.auralis.mobile.playback

import com.bobo.auralis.mobile.library.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import java.util.UUID

class PlaybackQueueTest {
    @Test
    fun `selected track starts inside the existing ordered queue`() {
        val first = trackId(1)
        val selected = trackId(2)
        val third = trackId(3)

        val queue = buildModePlaybackQueue(
            selected,
            listOf(first, selected, third).map { PlaybackQueueTrack(it, null, null, null) },
            PlaybackMode.Sequential,
        )

        assertEquals(listOf(first, selected, third), queue.tracks.map { it.trackId })
        assertEquals(1, queue.startIndex)
    }

    @Test
    fun `selected track is retained when library snapshot does not contain it`() {
        val selected = trackId(1)
        val other = trackId(2)

        val queue = buildModePlaybackQueue(
            selected,
            listOf(other).map { PlaybackQueueTrack(it, null, null, null) },
            PlaybackMode.Sequential,
        )

        assertEquals(listOf(selected, other), queue.tracks.map { it.trackId })
        assertEquals(0, queue.startIndex)
    }

    @Test
    fun `duplicate candidates are removed without changing their order`() {
        val selected = trackId(1)
        val other = trackId(2)

        val queue = buildModePlaybackQueue(
            selected,
            listOf(selected, other, selected, other).map { PlaybackQueueTrack(it, null, null, null) },
            PlaybackMode.Sequential,
        )

        assertEquals(listOf(selected, other), queue.tracks.map { it.trackId })
        assertEquals(0, queue.startIndex)
    }

    @Test
    fun `random tracks are unweighted and do not repeat inside one round`() {
        val tracks = (1L..8L).map { queueTrack(it, album = "album-$it", track = 1) }
        val selected = tracks[3].trackId

        val queue = buildModePlaybackQueue(
            selectedTrackId = selected,
            candidates = tracks,
            mode = PlaybackMode.ShuffleTracks,
            random = Random(7L),
        )

        assertEquals(selected, queue.tracks.first().trackId)
        assertEquals(tracks.map { it.trackId }.toSet(), queue.tracks.map { it.trackId }.toSet())
        assertEquals(queue.tracks.size, queue.tracks.distinctBy { it.trackId }.size)
    }

    @Test
    fun `random albums keep disc and track order inside every album`() {
        val a2 = queueTrack(2, album = "a", disc = 1, track = 2)
        val b2 = queueTrack(4, album = "b", disc = 2, track = 1)
        val a1 = queueTrack(1, album = "a", disc = 1, track = 1)
        val b1 = queueTrack(3, album = "b", disc = 1, track = 9)

        val queue = buildModePlaybackQueue(
            selectedTrackId = a2.trackId,
            candidates = listOf(a2, b2, a1, b1),
            mode = PlaybackMode.ShuffleAlbums,
            random = Random(9L),
        )

        assertEquals(listOf(a1.trackId, a2.trackId), queue.tracks.take(2).map { it.trackId })
        assertEquals(1, queue.startIndex)
        assertEquals(listOf(b1.trackId, b2.trackId), queue.tracks.drop(2).map { it.trackId })
    }

    @Test
    fun `playback modes map to native repeat behavior`() {
        assertEquals(androidx.media3.common.Player.REPEAT_MODE_OFF, playbackRepeatMode(PlaybackMode.Sequential))
        assertEquals(androidx.media3.common.Player.REPEAT_MODE_ALL, playbackRepeatMode(PlaybackMode.RepeatAll))
        assertEquals(androidx.media3.common.Player.REPEAT_MODE_ONE, playbackRepeatMode(PlaybackMode.RepeatOne))
        assertEquals(androidx.media3.common.Player.REPEAT_MODE_OFF, playbackRepeatMode(PlaybackMode.ShuffleTracks))
        assertEquals(androidx.media3.common.Player.REPEAT_MODE_OFF, playbackRepeatMode(PlaybackMode.ShuffleAlbums))
        assertEquals(PlaybackMode.Sequential, PlaybackMode.fromKey("unknown"))
        assertTrue(PlaybackMode.ShuffleTracks.usesRandomRounds)
    }

    @Test
    fun `album sorting preserves ties and puts missing positions last`() {
        val sameFirst = queueTrack(1, "album", disc = 1, track = 2)
        val sameSecond = queueTrack(2, "album", disc = 1, track = 2)
        val missingTrack = PlaybackQueueTrack(trackId(3), "album", 1, null)
        val secondDisc = queueTrack(4, "album", disc = 2, track = 1)
        val missingDisc = PlaybackQueueTrack(trackId(5), "album", null, 1)
        val missingBoth = PlaybackQueueTrack(trackId(6), "album", null, null)
        val missingBothAgain = PlaybackQueueTrack(trackId(7), "album", null, null)
        val input = listOf(missingBoth, missingTrack, sameFirst, secondDisc, missingDisc, sameSecond, missingBothAgain)
        val expected = listOf(sameFirst, sameSecond, missingTrack, secondDisc, missingDisc, missingBoth, missingBothAgain)

        val queue = buildModePlaybackQueue(sameSecond.trackId, input, PlaybackMode.ShuffleAlbums, Random(1))
        assertEquals(expected, queue.tracks)
        assertEquals(1, queue.startIndex)
        assertEquals(expected, buildNextRandomRound(input, PlaybackMode.ShuffleAlbums, Random(2)))
    }

    @Test
    fun `albumless tracks remain independent blocks across random rounds`() {
        val first = queueTrack(1, null, track = 2)
        val second = queueTrack(2, null, track = 1)
        val albumFirst = queueTrack(3, "album", track = 1)
        val albumSecond = queueTrack(4, "album", track = 2)
        val input = listOf(first, second, albumSecond, albumFirst, first)

        val queue = buildModePlaybackQueue(second.trackId, input, PlaybackMode.ShuffleAlbums, Random(3))
        assertEquals(second, queue.tracks.first())
        val round = buildNextRandomRound(queue.tracks, PlaybackMode.ShuffleAlbums, Random(4))
        assertEquals(input.distinct().toSet(), round.toSet())
        assertEquals(4, round.size)
        assertEquals(round.indexOf(albumFirst) + 1, round.indexOf(albumSecond))
    }

    private fun trackId(value: Long): TrackId = TrackId(UUID(0L, value))

    private fun queueTrack(
        value: Long,
        album: String?,
        disc: Int = 1,
        track: Int,
    ): PlaybackQueueTrack = PlaybackQueueTrack(trackId(value), album, disc, track)
}
