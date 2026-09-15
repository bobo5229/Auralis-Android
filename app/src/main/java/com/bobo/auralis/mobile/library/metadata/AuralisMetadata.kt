package com.bobo.auralis.mobile.library.metadata

/**
 * Auralis structured audio metadata model.
 *
 * Produced by interpreting raw container metadata (ID3v2, MP4, Xiph) according
 * strictly to Auralis metadata rules defined in METADATA.md.
 */
data class AuralisMetadata(
    val title: String?,
    val artists: List<String>,
    val albumArtists: List<String>,
    val album: String?,
    val genres: List<String>,
    val date: String?,
    val trackNumber: Int?,
    val trackTotal: Int?,
    val discNumber: Int?,
    val discTotal: Int?,
    val embeddedLyrics: String?,
    val hasEmbeddedArtwork: Boolean,
    val artworkBytes: Int,
    val artworkData: ByteArray? = null,
    val durationMs: Long,
    val bitrateKbps: Int,
    val sampleRateHz: Int,
    val mimeType: String,
    val rawMetadata: Metadata? = null,
)
