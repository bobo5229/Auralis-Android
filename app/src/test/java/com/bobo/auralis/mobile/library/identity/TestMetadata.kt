package com.bobo.auralis.mobile.library.identity

import com.bobo.auralis.mobile.library.metadata.AuralisMetadata

/**
 * Builds interpreted metadata for identity tests.
 *
 * Every field defaults to "absent" so a test only states what it is actually
 * about. Fields that §9 excludes from identity are still settable, because the
 * matrix has to prove that changing them does not move the key.
 */
internal fun auralisMetadata(
    title: String? = null,
    artists: List<String> = emptyList(),
    albumArtists: List<String> = emptyList(),
    album: String? = null,
    genres: List<String> = emptyList(),
    date: String? = null,
    trackNumber: Int? = null,
    trackTotal: Int? = null,
    discNumber: Int? = null,
    discTotal: Int? = null,
    embeddedLyrics: String? = null,
    hasEmbeddedArtwork: Boolean = false,
    artworkBytes: Int = 0,
    durationMs: Long = 0L,
    bitrateKbps: Int = 0,
    sampleRateHz: Int = 0,
    mimeType: String = "audio/mpeg",
): AuralisMetadata = AuralisMetadata(
    title = title,
    artists = artists,
    albumArtists = albumArtists,
    album = album,
    genres = genres,
    date = date,
    trackNumber = trackNumber,
    trackTotal = trackTotal,
    discNumber = discNumber,
    discTotal = discTotal,
    embeddedLyrics = embeddedLyrics,
    hasEmbeddedArtwork = hasEmbeddedArtwork,
    artworkBytes = artworkBytes,
    durationMs = durationMs,
    bitrateKbps = bitrateKbps,
    sampleRateHz = sampleRateHz,
    mimeType = mimeType,
)
