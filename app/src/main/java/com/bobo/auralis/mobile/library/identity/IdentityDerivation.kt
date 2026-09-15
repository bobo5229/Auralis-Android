package com.bobo.auralis.mobile.library.identity

import com.bobo.auralis.mobile.library.metadata.AuralisMetadata

/**
 * Everything the identity layer contributes to reconciliation.
 *
 * Mirrors `ResolvedObservation` from `docs/phase3a/01_IDENTITY.md` §45, minus
 * the observation itself, which belongs to the scan/domain model layer (Step C).
 */
data class DerivedIdentity(
    val albumIdentity: AlbumIdentity?,
    val albumKey: AlbumKey?,
    val trackIdentity: TrackIdentity,
    val trackKey: TrackKey,
    val trackKeyStrength: TrackKeyStrength,
)

/**
 * Derives identity from interpreted Phase 2C metadata.
 *
 * This is the boundary named in §43: interpreted metadata flows into identity
 * calculation, and identity calculation feeds the physical source model. Nothing
 * here reads files, touches SAF, or knows about Room.
 *
 * [AuralisMetadata] fields that §9 excludes from identity — genres, duration,
 * bitrate, sample rate, mime type, artwork, lyrics, trackTotal, discTotal — are
 * intentionally never read. Composer does not participate either.
 */
object IdentityDerivation {

    fun derive(metadata: AuralisMetadata): DerivedIdentity {
        val albumIdentity = AlbumIdentity.from(
            album = metadata.album,
            albumArtists = metadata.albumArtists,
            date = metadata.date,
        )

        val trackIdentity = TrackIdentity(
            albumIdentity = albumIdentity,
            title = IdentityTextNormalizerV1.normalize(metadata.title),
            trackArtists = IdentityTextNormalizerV1.canonicalCollection(metadata.artists),
            discNumber = metadata.discNumber,
            trackNumber = metadata.trackNumber,
        )

        return DerivedIdentity(
            albumIdentity = albumIdentity,
            albumKey = albumIdentity?.let(AlbumKeyV1::derive),
            trackIdentity = trackIdentity,
            trackKey = TrackKeyV1.derive(trackIdentity),
            trackKeyStrength = TrackKeyV1.strengthOf(trackIdentity),
        )
    }
}
