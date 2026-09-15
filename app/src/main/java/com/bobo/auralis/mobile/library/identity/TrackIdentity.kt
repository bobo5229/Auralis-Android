package com.bobo.auralis.mobile.library.identity

/**
 * Canonical track identity material, version 1.
 *
 * Frozen by `docs/phase3a/01_IDENTITY.md` §8.1:
 *
 * ```text
 * TrackIdentityV1 { albumIdentity; title; trackArtists; discNumber; trackNumber }
 * ```
 *
 * The album component is the full [AlbumIdentity] material (album title, album
 * artists, date), not just an `AlbumKey` hash, so the TrackKey specification
 * stays completely self-describing.
 *
 * Deliberately absent, per §9: genre, bitrate, sample rate, MIME/codec,
 * duration, file size, path, URI, documentId, artwork, lyrics, trackTotal,
 * discTotal, file name and composer. Editing any of those must never turn a
 * song into a different track, and a FLAC re-encoded to ALAC must stay
 * associated with the same entry.
 *
 * Values are canonical when built through [IdentityDerivation]; key derivation
 * re-canonicalizes defensively in any case.
 */
data class TrackIdentity(
    val albumIdentity: AlbumIdentity?,
    val title: String?,
    val trackArtists: List<String>,
    val discNumber: Int?,
    val trackNumber: Int?,
)
