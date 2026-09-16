package com.bobo.auralis.mobile.library.reconciliation

import com.bobo.auralis.mobile.library.model.ParseState
import com.bobo.auralis.mobile.library.model.PlayabilityState
import com.bobo.auralis.mobile.library.model.SourceAvailabilityState
import com.bobo.auralis.mobile.library.model.TrackSourceId

/**
 * Representation of a source eligible to be ranked as an active candidate for a track.
 *
 * Frozen by `docs/phase3a/04_RECONCILIATION.md` §26 & §27.
 */
data class CandidateSource(
    val sourceId: TrackSourceId,
    val rootPriority: Int,
    val relativePath: String,
    val availabilityState: SourceAvailabilityState,
    val parseState: ParseState,
    val playabilityState: PlayabilityState,
)

/**
 * Duplicate candidate ranking and selection rules.
 *
 * Frozen by `docs/phase3a/04_RECONCILIATION.md` §26 & §27:
 *
 * Eligibility:
 * - availabilityState == AVAILABLE
 * - parseState == PARSED
 * - playabilityState != UNPLAYABLE (UNKNOWN is eligible)
 *
 * Ranking comparator:
 * 1. playability: PLAYABLE before UNKNOWN
 * 2. root priority: smaller first (earlier added root has priority)
 * 3. relativePath: deterministic lexical ascending
 * 4. sourceId: deterministic final tie breaker
 *
 * Audio format, bitrate, file size, sample rate NEVER participate in candidate ranking.
 */
object DuplicateSourceRanking {

    fun isEligible(source: CandidateSource): Boolean =
        source.availabilityState == SourceAvailabilityState.AVAILABLE &&
            source.parseState == ParseState.PARSED &&
            source.playabilityState != PlayabilityState.UNPLAYABLE

    /**
     * Comparator implementing §27 ranking.
     * Smaller value means higher priority (comes first).
     */
    val comparator: Comparator<CandidateSource> = Comparator { a, b ->
        // 1. playability: PLAYABLE before UNKNOWN
        val playabilityCompare = playabilityRank(a.playabilityState).compareTo(playabilityRank(b.playabilityState))
        if (playabilityCompare != 0) return@Comparator playabilityCompare

        // 2. root priority: smaller first
        val rootCompare = a.rootPriority.compareTo(b.rootPriority)
        if (rootCompare != 0) return@Comparator rootCompare

        // 3. relativePath: deterministic lexical ascending
        val pathCompare = a.relativePath.compareTo(b.relativePath)
        if (pathCompare != 0) return@Comparator pathCompare

        // 4. sourceId: deterministic final tie breaker
        a.sourceId.value.toString().compareTo(b.sourceId.value.toString())
    }

    private fun playabilityRank(state: PlayabilityState): Int = when (state) {
        PlayabilityState.PLAYABLE -> 0
        PlayabilityState.UNKNOWN -> 1
        PlayabilityState.UNPLAYABLE -> 2
    }

    /**
     * Filters eligible candidates and selects the single highest-priority active source,
     * or null if no candidates are eligible.
     */
    fun selectActiveSource(candidates: Collection<CandidateSource>): CandidateSource? =
        candidates
            .filter { isEligible(it) }
            .minWithOrNull(comparator)
}
