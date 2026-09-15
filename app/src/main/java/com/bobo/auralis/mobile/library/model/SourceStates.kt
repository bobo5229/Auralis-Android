package com.bobo.auralis.mobile.library.model

/**
 * Whether a physical source can currently be confirmed to exist.
 *
 * Frozen by `docs/phase3a/02_SOURCE_MODEL.md` §22.
 */
enum class SourceAvailabilityState {
    /** The source was confirmed to exist in this round. */
    AVAILABLE,

    /**
     * Auralis successfully scanned the observable scope this source belongs to and
     * confirmed the document no longer exists.
     */
    MISSING,

    /**
     * Existence cannot be reliably confirmed: revoked root permission, provider
     * error, failed directory query.
     *
     * §22: `UNREACHABLE` must never be converted into [MISSING] automatically. A
     * temporarily unreachable directory must not look like a deletion.
     */
    UNREACHABLE,
}

/**
 * Whether metadata extraction ever succeeded for a source.
 *
 * Frozen by `docs/phase3a/02_SOURCE_MODEL.md` §23.
 */
enum class ParseState {
    /** Not parsed yet. */
    NOT_PARSED,

    /** Phase 2C interpretation succeeded and produced `AuralisMetadata`. */
    PARSED,

    /**
     * Interpretation failed.
     *
     * The source is still kept as a physical TrackSource with its failure
     * diagnostics, because an unparseable file must never be silently dropped. A
     * failed source does not take part in semantic TrackKey matching.
     */
    FAILED,
}

/**
 * Whether the playback pipeline has confirmed a source can actually be played.
 *
 * Frozen by `docs/phase3a/02_SOURCE_MODEL.md` §24.
 */
enum class PlayabilityState {
    /**
     * Never confirmed by playback.
     *
     * A successful scan or a successful metadata parse must not claim
     * [PLAYABLE], because neither proves the decoder can open the file. Sources in
     * this state are still eligible to become the active source, since Auralis
     * does not play every file up front just to find out.
     */
    UNKNOWN,

    /** The playback pipeline opened this source successfully. */
    PLAYABLE,

    /** The playback pipeline explicitly failed; active source selection must rerun. */
    UNPLAYABLE,
}

/**
 * Why a source could not be parsed.
 *
 * §12 and §23 fix the three field names (`failureStage`, `failureCode`,
 * `failureMessage`) but not their value domain, so stage and code stay plain
 * strings here instead of an invented enum. The persistent columns belong to
 * `TrackSourceEntity` in Step D.
 */
data class SourceFailure(
    val stage: String,
    val code: String,
    val message: String? = null,
)
