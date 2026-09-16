package com.bobo.auralis.mobile.library.reconciliation

import com.bobo.auralis.mobile.library.identity.TrackKey
import com.bobo.auralis.mobile.library.identity.TrackKeyStrength
import com.bobo.auralis.mobile.library.model.SourceAvailabilityState
import com.bobo.auralis.mobile.library.model.TrackId
import com.bobo.auralis.mobile.library.model.TrackSourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests covering pure reconciliation decision matrix in `docs/phase3a/04_RECONCILIATION.md` §59.
 */
class ReconciliationDecisionTest {

    private val baseObservation = ReconcileObservation(
        provider = "com.android.externalstorage.documents",
        documentId = "doc_123",
        rootPriority = 0,
        relativePath = "album/song.flac",
        albumKeyVersion = 1,
        albumKeyHash = "album_hash_v1",
        trackKey = TrackKey(version = 1, hash = "track_hash_v1"),
        trackKeyStrength = TrackKeyStrength.STRONG,
    )

    private val baseExistingSource = ExistingSourceSnapshot(
        sourceId = TrackSourceId.random(),
        trackId = TrackId.random(),
        provider = "com.android.externalstorage.documents",
        documentId = "doc_123",
        albumKeyVersion = 1,
        albumKeyHash = "album_hash_v1",
        trackKeyVersion = 1,
        trackKeyHash = "track_hash_v1",
    )

    @Test
    fun `same document key and same Album preserves sourceId and TrackId`() {
        // §59: same document key + same Album -> preserve sourceId + TrackId
        val decision = ReconciliationDecisionEngine.decide(
            observation = baseObservation,
            existingPhysicalSource = baseExistingSource,
            semanticMatchingTracks = emptyList(),
        )

        assertTrue(decision is ReconcileDecision.RetainPhysicalTrack)
        val retain = decision as ReconcileDecision.RetainPhysicalTrack
        assertEquals(baseExistingSource.sourceId, retain.sourceId)
        assertEquals(baseExistingSource.trackId, retain.trackId)
        assertEquals(false, retain.trackKeyChanged)
    }

    @Test
    fun `same document key and title or artist changed but same Album preserves TrackId and flags trackKeyChanged`() {
        // §59: same document key + title changed + same Album -> preserve TrackId
        // §59: same document key + Artist changed + same Album -> preserve TrackId
        val modifiedTrackKeyObservation = baseObservation.copy(
            trackKey = TrackKey(version = 1, hash = "new_track_hash_after_title_or_artist_edit"),
        )

        val decision = ReconciliationDecisionEngine.decide(
            observation = modifiedTrackKeyObservation,
            existingPhysicalSource = baseExistingSource,
            semanticMatchingTracks = emptyList(),
        )

        assertTrue(decision is ReconcileDecision.RetainPhysicalTrack)
        val retain = decision as ReconcileDecision.RetainPhysicalTrack
        assertEquals(baseExistingSource.sourceId, retain.sourceId)
        assertEquals(baseExistingSource.trackId, retain.trackId)
        assertEquals(true, retain.trackKeyChanged)
    }

    @Test
    fun `same document key but AlbumKey changed breaks album boundary and detaches old Track`() {
        // §59: same document key + AlbumKey changed -> detach old Track + new/different Track
        val modifiedAlbumObservation = baseObservation.copy(
            albumKeyHash = "different_album_hash",
        )

        val decision = ReconciliationDecisionEngine.decide(
            observation = modifiedAlbumObservation,
            existingPhysicalSource = baseExistingSource,
            semanticMatchingTracks = emptyList(),
        )

        assertTrue(decision is ReconcileDecision.BreakAlbumBoundary)
        val breakBoundary = decision as ReconcileDecision.BreakAlbumBoundary
        assertEquals(baseExistingSource.sourceId, breakBoundary.sourceId)
        assertEquals(baseExistingSource.trackId, breakBoundary.oldTrackId)
    }

    @Test
    fun `new document key and Strong TrackKey unique match attaches to existing Track`() {
        // §59: new document key + Strong TrackKey match -> attach existing Track
        // §59: FLAC missing + ALAC appears + same Strong TrackKey -> same TrackId
        val existingTrackId = TrackId.random()

        val decision = ReconciliationDecisionEngine.decide(
            observation = baseObservation,
            existingPhysicalSource = null, // new document key
            semanticMatchingTracks = listOf(existingTrackId),
        )

        assertTrue(decision is ReconcileDecision.AttachToExistingTrack)
        val attach = decision as ReconcileDecision.AttachToExistingTrack
        assertEquals(existingTrackId, attach.existingTrackId)
    }

    @Test
    fun `new document key and Weak TrackKey match always creates new Track`() {
        // §59: new document key + Weak TrackKey match -> create new Track
        val weakObservation = baseObservation.copy(
            trackKeyStrength = TrackKeyStrength.WEAK,
        )

        val decision = ReconciliationDecisionEngine.decide(
            observation = weakObservation,
            existingPhysicalSource = null,
            semanticMatchingTracks = listOf(TrackId.random()), // Even if weak key happens to match
        )

        assertTrue(decision is ReconcileDecision.CreateNewTrack)
        val create = decision as ReconcileDecision.CreateNewTrack
        assertEquals(CreateTrackReason.WEAK_KEY_CANNOT_AUTO_MERGE, create.reason)
    }

    @Test
    fun `new document key and Strong TrackKey with no matches creates new Track`() {
        // §59: new document key + Strong TrackKey no match -> create new Track
        val decision = ReconciliationDecisionEngine.decide(
            observation = baseObservation,
            existingPhysicalSource = null,
            semanticMatchingTracks = emptyList(),
        )

        assertTrue(decision is ReconcileDecision.CreateNewTrack)
        val create = decision as ReconcileDecision.CreateNewTrack
        assertEquals(CreateTrackReason.NEW_STRONG_KEY_NO_MATCH, create.reason)
    }

    @Test
    fun `Strong TrackKey matches more than one existing Track flags ambiguity and creates new Track`() {
        // §59: Strong TrackKey matches >1 existing Track -> ambiguous; do not merge
        val trackId1 = TrackId.random()
        val trackId2 = TrackId.random()

        val decision = ReconciliationDecisionEngine.decide(
            observation = baseObservation,
            existingPhysicalSource = null,
            semanticMatchingTracks = listOf(trackId1, trackId2),
        )

        assertTrue(decision is ReconcileDecision.AmbiguousStrongMatchCreateNewTrack)
        val ambiguous = decision as ReconcileDecision.AmbiguousStrongMatchCreateNewTrack
        assertEquals(listOf(trackId1, trackId2), ambiguous.matchingTrackIds)
    }

    @Test
    fun `root unavailable leads to UNREACHABLE state and successful scan leads to MISSING`() {
        // §59: root unavailable -> source UNREACHABLE, not MISSING
        // §59: successful root scan no longer sees source -> MISSING
        assertEquals(
            SourceAvailabilityState.UNREACHABLE,
            ReconciliationDecisionEngine.decideMissingState(wasRootReachable = false),
        )

        assertEquals(
            SourceAvailabilityState.MISSING,
            ReconciliationDecisionEngine.decideMissingState(wasRootReachable = true),
        )
    }
}
