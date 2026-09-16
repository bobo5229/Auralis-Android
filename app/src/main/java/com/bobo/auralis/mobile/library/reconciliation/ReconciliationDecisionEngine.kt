package com.bobo.auralis.mobile.library.reconciliation

import com.bobo.auralis.mobile.library.identity.TrackKey
import com.bobo.auralis.mobile.library.identity.TrackKeyStrength
import com.bobo.auralis.mobile.library.model.SourceAvailabilityState
import com.bobo.auralis.mobile.library.model.TrackId
import com.bobo.auralis.mobile.library.model.TrackSourceId

/**
 * An observation ready for reconciliation decision.
 *
 * All values are resolved pure Kotlin types, decoupled from Android/SAF/Room.
 */
data class ReconcileObservation(
    val provider: String,
    val documentId: String,
    val rootPriority: Int,
    val relativePath: String,
    val albumKeyVersion: Int?,
    val albumKeyHash: String?,
    val trackKey: TrackKey,
    val trackKeyStrength: TrackKeyStrength,
)

/**
 * Snapshot of an existing source in storage.
 */
data class ExistingSourceSnapshot(
    val sourceId: TrackSourceId,
    val trackId: TrackId,
    val provider: String,
    val documentId: String,
    val albumKeyVersion: Int?,
    val albumKeyHash: String?,
    val trackKeyVersion: Int,
    val trackKeyHash: String,
)

/**
 * Pure decisions output by the reconciliation engine.
 *
 * Frozen by `docs/phase3a/04_RECONCILIATION.md` §28–§39.
 */
sealed interface ReconcileDecision {

    /**
     * §28, §31: Same physical source, and Album boundary unchanged.
     * The existing TrackId is kept. Even if Title, Artist, Track Number or
     * TrackKey changed, trackId remains the same (§31).
     */
    data class RetainPhysicalTrack(
        val sourceId: TrackSourceId,
        val trackId: TrackId,
        val trackKeyChanged: Boolean,
    ) : ReconcileDecision

    /**
     * §30: Same physical source, but AlbumKey changed!
     * Album hard boundary violated: the source must detach from its old Track.
     * A different track (or new track) must be attached.
     */
    data class BreakAlbumBoundary(
        val sourceId: TrackSourceId,
        val oldTrackId: TrackId,
    ) : ReconcileDecision

    /**
     * §32, §35: New physical source with Strong TrackKey uniquely matching one existing Track.
     * Attach this source to that existing Track.
     */
    data class AttachToExistingTrack(
        val existingTrackId: TrackId,
    ) : ReconcileDecision

    /**
     * §32: New physical source with Strong TrackKey having 0 matches, OR
     * with Weak TrackKey (Weak never auto-merges across physical sources).
     */
    data class CreateNewTrack(
        val reason: CreateTrackReason,
    ) : ReconcileDecision

    /**
     * §33: New physical source with Strong TrackKey matching >1 existing Tracks.
     * Must NOT arbitrarily pick one or merge them. Creates a new Track and flags ambiguity.
     */
    data class AmbiguousStrongMatchCreateNewTrack(
        val matchingTrackIds: List<TrackId>,
    ) : ReconcileDecision
}

enum class CreateTrackReason {
    NEW_STRONG_KEY_NO_MATCH,
    WEAK_KEY_CANNOT_AUTO_MERGE,
}

/**
 * Pure decision engine for reconciliation.
 *
 * Implements §28, §30, §31, §32, §33, §37.
 */
object ReconciliationDecisionEngine {

    /**
     * Reconciles an observation against existing sources and candidate tracks.
     *
     * @param observation The new observation from scan
     * @param existingPhysicalSource Existing source with matching (provider, documentId), if any (§28)
     * @param semanticMatchingTracks Existing tracks whose (trackKeyVersion, trackKeyHash) match (§32)
     */
    fun decide(
        observation: ReconcileObservation,
        existingPhysicalSource: ExistingSourceSnapshot?,
        semanticMatchingTracks: List<TrackId>,
    ): ReconcileDecision {
        // 1. Physical continuity matching (§28)
        if (existingPhysicalSource != null) {
            // §30 Album hard boundary check:
            // If albumKey changed (e.g. was Album A, now Album B, or was null, now Album B),
            // it cannot remain the same logical track.
            val albumChanged = existingPhysicalSource.albumKeyVersion != observation.albumKeyVersion ||
                existingPhysicalSource.albumKeyHash != observation.albumKeyHash

            if (albumChanged) {
                return ReconcileDecision.BreakAlbumBoundary(
                    sourceId = existingPhysicalSource.sourceId,
                    oldTrackId = existingPhysicalSource.trackId,
                )
            }

            // Same physical source, same AlbumKey -> keep TrackId (§31)
            val trackKeyChanged = existingPhysicalSource.trackKeyVersion != observation.trackKey.version ||
                existingPhysicalSource.trackKeyHash != observation.trackKey.hash

            return ReconcileDecision.RetainPhysicalTrack(
                sourceId = existingPhysicalSource.sourceId,
                trackId = existingPhysicalSource.trackId,
                trackKeyChanged = trackKeyChanged,
            )
        }

        // 2. New physical source: semantic matching (§32)
        return when (observation.trackKeyStrength) {
            TrackKeyStrength.WEAK -> {
                // §32: Weak TrackKey always creates a new track when physical continuity misses.
                ReconcileDecision.CreateNewTrack(CreateTrackReason.WEAK_KEY_CANNOT_AUTO_MERGE)
            }
            TrackKeyStrength.STRONG -> {
                when {
                    semanticMatchingTracks.size == 1 -> {
                        // Unique strong match -> attach (§32, §35)
                        ReconcileDecision.AttachToExistingTrack(semanticMatchingTracks.single())
                    }
                    semanticMatchingTracks.size > 1 -> {
                        // §33: Ambiguous strong match -> do NOT arbitrarily merge, create new track
                        ReconcileDecision.AmbiguousStrongMatchCreateNewTrack(semanticMatchingTracks)
                    }
                    else -> {
                        // 0 matches -> create new track (§32)
                        ReconcileDecision.CreateNewTrack(CreateTrackReason.NEW_STRONG_KEY_NO_MATCH)
                    }
                }
            }
        }
    }

    /**
     * §22, §37: Decides the availability state for a source not seen during a scan session.
     *
     * @param wasRootReachable Whether the root/scope of this source was successfully and reachable-scanned.
     *   If the root was UNAVAILABLE or had an access failure, source must be UNREACHABLE, never MISSING (§22).
     */
    fun decideMissingState(wasRootReachable: Boolean): SourceAvailabilityState {
        return if (wasRootReachable) {
            SourceAvailabilityState.MISSING
        } else {
            SourceAvailabilityState.UNREACHABLE
        }
    }
}
