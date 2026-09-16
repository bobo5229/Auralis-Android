package com.bobo.auralis.mobile.library.reconciliation

import com.bobo.auralis.mobile.library.model.ParseState
import com.bobo.auralis.mobile.library.model.PlayabilityState
import com.bobo.auralis.mobile.library.model.SourceAvailabilityState
import com.bobo.auralis.mobile.library.model.TrackSourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

/**
 * Tests covering duplicate selection specifications in `docs/phase3a/04_RECONCILIATION.md` §26, §27 & §60.
 */
class DuplicateSelectionTest {

    private fun createCandidate(
        sourceId: TrackSourceId = TrackSourceId.random(),
        rootPriority: Int = 0,
        relativePath: String = "music/song.flac",
        availabilityState: SourceAvailabilityState = SourceAvailabilityState.AVAILABLE,
        parseState: ParseState = ParseState.PARSED,
        playabilityState: PlayabilityState = PlayabilityState.PLAYABLE,
    ): CandidateSource = CandidateSource(
        sourceId = sourceId,
        rootPriority = rootPriority,
        relativePath = relativePath,
        availabilityState = availabilityState,
        parseState = parseState,
        playabilityState = playabilityState,
    )

    @Test
    fun `PLAYABLE vs UNKNOWN prefers PLAYABLE`() {
        // §60: PLAYABLE vs UNKNOWN -> PLAYABLE
        val playable = createCandidate(playabilityState = PlayabilityState.PLAYABLE)
        val unknown = createCandidate(playabilityState = PlayabilityState.UNKNOWN)

        assertEquals(playable, DuplicateSourceRanking.selectActiveSource(listOf(unknown, playable)))
        assertEquals(playable, DuplicateSourceRanking.selectActiveSource(listOf(playable, unknown)))
    }

    @Test
    fun `UNKNOWN root 0 vs UNKNOWN root 1 prefers root 0`() {
        // §60: UNKNOWN root 0 vs UNKNOWN root 1 -> root 0
        val root0 = createCandidate(rootPriority = 0, playabilityState = PlayabilityState.UNKNOWN)
        val root1 = createCandidate(rootPriority = 1, playabilityState = PlayabilityState.UNKNOWN)

        assertEquals(root0, DuplicateSourceRanking.selectActiveSource(listOf(root1, root0)))
        assertEquals(root0, DuplicateSourceRanking.selectActiveSource(listOf(root0, root1)))
    }

    @Test
    fun `same root lexical path ordering prefers a over b`() {
        // §60: same root: a/song.m4a vs b/song.flac -> a/song.m4a
        val pathA = createCandidate(relativePath = "a/song.m4a")
        val pathB = createCandidate(relativePath = "b/song.flac")

        assertEquals(pathA, DuplicateSourceRanking.selectActiveSource(listOf(pathB, pathA)))
        assertEquals(pathA, DuplicateSourceRanking.selectActiveSource(listOf(pathA, pathB)))
    }

    @Test
    fun `root earlier has MP3 and root later has FLAC prefers earlier root`() {
        // §60: root earlier has MP3, root later has FLAC -> MP3 (format does not matter)
        val earlierMp3 = createCandidate(rootPriority = 0, relativePath = "song.mp3")
        val laterFlac = createCandidate(rootPriority = 1, relativePath = "song.flac")

        assertEquals(earlierMp3, DuplicateSourceRanking.selectActiveSource(listOf(laterFlac, earlierMp3)))
        assertEquals(earlierMp3, DuplicateSourceRanking.selectActiveSource(listOf(earlierMp3, laterFlac)))
    }

    @Test
    fun `root earlier has 128 kbps and root later has 320 kbps prefers earlier root`() {
        // §60: bitrate / audio properties never participate; root priority wins
        val earlierLowerBitrate = createCandidate(rootPriority = 0, relativePath = "low.mp3")
        val laterHigherBitrate = createCandidate(rootPriority = 1, relativePath = "high.mp3")

        assertEquals(earlierLowerBitrate, DuplicateSourceRanking.selectActiveSource(listOf(laterHigherBitrate, earlierLowerBitrate)))
    }

    @Test
    fun `candidate UNPLAYABLE is completely excluded`() {
        // §60: candidate UNPLAYABLE -> excluded
        val unplayable = createCandidate(playabilityState = PlayabilityState.UNPLAYABLE)
        assertNull(DuplicateSourceRanking.selectActiveSource(listOf(unplayable)))

        val playable = createCandidate(playabilityState = PlayabilityState.PLAYABLE)
        assertEquals(playable, DuplicateSourceRanking.selectActiveSource(listOf(unplayable, playable)))
    }

    @Test
    fun `candidates not parsed or missing are completely excluded`() {
        val notParsed = createCandidate(parseState = ParseState.NOT_PARSED)
        val parseFailed = createCandidate(parseState = ParseState.FAILED)
        val missing = createCandidate(availabilityState = SourceAvailabilityState.MISSING)
        val unreachable = createCandidate(availabilityState = SourceAvailabilityState.UNREACHABLE)

        assertNull(DuplicateSourceRanking.selectActiveSource(listOf(notParsed, parseFailed, missing, unreachable)))

        val playable = createCandidate(playabilityState = PlayabilityState.PLAYABLE)
        assertEquals(playable, DuplicateSourceRanking.selectActiveSource(listOf(notParsed, parseFailed, missing, unreachable, playable)))
    }

    @Test
    fun `tie breaker on sourceId produces deterministic winner regardless of input permutation`() {
        val id1 = TrackSourceId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val id2 = TrackSourceId(UUID.fromString("00000000-0000-0000-0000-000000000002"))

        val cand1 = createCandidate(sourceId = id1, rootPriority = 0, relativePath = "same.mp3")
        val cand2 = createCandidate(sourceId = id2, rootPriority = 0, relativePath = "same.mp3")

        // id1 is lexicographically smaller than id2
        assertEquals(cand1, DuplicateSourceRanking.selectActiveSource(listOf(cand1, cand2)))
        assertEquals(cand1, DuplicateSourceRanking.selectActiveSource(listOf(cand2, cand1)))
    }
}
