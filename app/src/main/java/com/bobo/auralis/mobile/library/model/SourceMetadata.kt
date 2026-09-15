package com.bobo.auralis.mobile.library.model

import com.bobo.auralis.mobile.library.identity.DerivedIdentity
import com.bobo.auralis.mobile.library.identity.TrackKeyStrength
import com.bobo.auralis.mobile.library.metadata.AuralisMetadata

/**
 * The metadata snapshot one physical source last parsed successfully.
 *
 * Frozen by `docs/phase3a/02_SOURCE_MODEL.md` §13. Every TrackSource keeps its
 * own snapshot. Without it, promoting a duplicate source after the active one
 * disappears would have to re-read the file, because the logical Track only ever
 * holds the active source's values.
 *
 * [artists], [albumArtists] and [genres] keep the original Phase 2C display
 * order. Only the identity layer sorts values into sets, and that sorted form
 * must never leak back into UI credits (§4).
 *
 * Deliberately absent: `rawMetadata` (debug/extraction diagnostics only) and
 * artwork bytes. Neither belongs in the library database (§13). Artwork presence
 * stays visible through [AuralisMetadata.hasEmbeddedArtwork] at extraction time
 * and gets its own identity in a later phase.
 *
 * `artists` / `albumArtists` / `genres` are stored as lists here; how Step D
 * serializes them into single columns is a persistence concern, not a domain one.
 */
data class SourceMetadata(
    val title: String?,
    val albumTitle: String?,
    val date: String?,
    val trackNumber: Int?,
    val trackTotal: Int?,
    val discNumber: Int?,
    val discTotal: Int?,
    val artists: List<String>,
    val albumArtists: List<String>,
    val genres: List<String>,
    val durationMs: Long,
    val bitrateKbps: Int,
    val sampleRateHz: Int,
    val mimeType: String,
    val trackKeyVersion: Int,
    val trackKeyHash: String,
    val trackKeyStrength: TrackKeyStrength,
    /**
     * Album key version, or null when the source has no album title and therefore
     * no album identity at all (§5.4). Version and hash are always null together.
     */
    val albumKeyVersion: Int?,
    val albumKeyHash: String?,
) {
    companion object {

        /**
         * Captures the snapshot for a successfully parsed source.
         *
         * Values are copied as interpreted, without re-canonicalization: this is
         * the display-side record of what the file actually said. Identity
         * canonicalization happens in the identity layer, which is why the key
         * version, hash and strength are carried along rather than recomputed.
         */
        fun from(metadata: AuralisMetadata, identity: DerivedIdentity): SourceMetadata =
            SourceMetadata(
                title = metadata.title,
                albumTitle = metadata.album,
                date = metadata.date,
                trackNumber = metadata.trackNumber,
                trackTotal = metadata.trackTotal,
                discNumber = metadata.discNumber,
                discTotal = metadata.discTotal,
                artists = metadata.artists,
                albumArtists = metadata.albumArtists,
                genres = metadata.genres,
                durationMs = metadata.durationMs,
                bitrateKbps = metadata.bitrateKbps,
                sampleRateHz = metadata.sampleRateHz,
                mimeType = metadata.mimeType,
                trackKeyVersion = identity.trackKey.version,
                trackKeyHash = identity.trackKey.hash,
                trackKeyStrength = identity.trackKeyStrength,
                albumKeyVersion = identity.albumKey?.version,
                albumKeyHash = identity.albumKey?.hash,
            )
    }
}
