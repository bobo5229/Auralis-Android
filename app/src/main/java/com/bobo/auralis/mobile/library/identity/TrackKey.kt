package com.bobo.auralis.mobile.library.identity

/**
 * Versioned track semantic hash.
 *
 * `TrackKey` exists only for reconciliation:
 *
 * - matching a new physical source against an existing logical track
 * - grouping duplicate sources
 * - reconnecting a track after it disappeared
 *
 * It is explicitly not track identity. A track keeps its stable `TrackId` while
 * its `TrackKey` changes, which is exactly why the two are separate types. It is
 * never a database primary key.
 */
data class TrackKey(
    val version: Int,
    val hash: String,
)

/**
 * How much evidence a `TrackKey` carries.
 *
 * Being able to compute a hash does not mean there is enough information to
 * merge automatically. A weak key may be computed and stored, but must never
 * merge across physical sources.
 */
enum class TrackKeyStrength {
    STRONG,
    WEAK,
}

/**
 * Track identity hash algorithm, version 1.
 *
 * Frozen by `docs/phase3a/01_IDENTITY.md` §8.1:
 *
 * ```text
 * TrackKeyV1 = SHA-256(
 *     versionMarker,
 *     AlbumIdentityV1,
 *     title,
 *     sortedDistinct(trackArtists),
 *     discNumber,
 *     trackNumber
 * )
 * ```
 */
object TrackKeyV1 {

    const val VERSION = 1

    private const val MARKER = "AURALIS_TRACK_V1"

    fun derive(identity: TrackIdentity): TrackKey = TrackKey(
        version = VERSION,
        hash = IdentityHasher()
            .marker(MARKER)
            .writeAlbumMaterial(identity.albumIdentity)
            .field("title").text(IdentityTextNormalizerV1.normalize(identity.title))
            .field("artists")
            .textList(IdentityTextNormalizerV1.canonicalCollection(identity.trackArtists))
            .field("discNumber").int(identity.discNumber)
            .field("trackNumber").int(identity.trackNumber)
            .hash(),
    )

    /**
     * Strength rule from §10:
     *
     * ```text
     * album != null
     * AND countPresent(title != null, artists.isNotEmpty(), trackNumber != null) >= 2
     * ```
     *
     * Disc number contributes nothing by itself; it only differentiates further.
     */
    fun strengthOf(identity: TrackIdentity): TrackKeyStrength {
        if (identity.albumIdentity == null) return TrackKeyStrength.WEAK
        val present = countPresent(
            identity.title != null,
            identity.trackArtists.isNotEmpty(),
            identity.trackNumber != null,
        )
        return if (present >= 2) TrackKeyStrength.STRONG else TrackKeyStrength.WEAK
    }

    private fun countPresent(vararg evidence: Boolean): Int = evidence.count { it }
}
