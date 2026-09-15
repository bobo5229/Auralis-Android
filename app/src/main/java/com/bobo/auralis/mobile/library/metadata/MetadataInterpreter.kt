/*
 * Part of tag key mapping aliases derived from Auxio Project (TagFields.kt)
 * Copyright (c) 2024 Auxio Project
 * Licensed under GNU General Public License v3.0 or later.
 *
 * Modified and implemented for Auralis Mobile according to Auralis metadata rules.
 */

package com.bobo.auralis.mobile.library.metadata

/**
 * Splits strings strictly by ASCII semicolon (';'), trims leading and trailing
 * whitespace, and drops empty/blank segments.
 *
 * Does NOT split on ',', '/', '&', '+', 'feat.', 'ft.', 'x', or any other character.
 * Order is preserved, and no case-insensitive deduplication or string merging is performed.
 */
fun splitSemicolonList(values: List<String>?): List<String> {
    if (values.isNullOrEmpty()) return emptyList()
    val result = mutableListOf<String>()
    for (v in values) {
        val parts = v.split(';')
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.isNotEmpty()) {
                result.add(trimmed)
            }
        }
    }
    return result
}

private val AURALIS_DATE_REGEX = Regex("""^(\d{4}-\d{2}-\d{2})(?:[T\s].*)?$""")

/**
 * Validates and extracts a canonical YYYY-MM-DD date string.
 *
 * If the string contains only a year or non-standard format, returns null.
 * Missing date remains null (no automatic fallback to year).
 */
fun parseAuralisDate(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val match = AURALIS_DATE_REGEX.matchEntire(raw.trim()) ?: return null
    return match.groupValues[1]
}

/**
 * Parses structured numeric track or disc positions like "1/12" or "1".
 * Returns Pair(index, total), where both must be positive integers if present.
 * If unparseable, returns Pair(null, null).
 */
fun parsePositionWithFallback(raw: String?, fallbackTotalRaw: String? = null): Pair<Int?, Int?> {
    if (raw.isNullOrBlank()) return Pair(null, null)
    val parts = raw.split('/')
    val index = parts.getOrNull(0)?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: return Pair(null, null)
    val totalFromSlash = parts.getOrNull(1)?.trim()?.toIntOrNull()?.takeIf { it > 0 }
    val total = totalFromSlash ?: fallbackTotalRaw?.trim()?.toIntOrNull()?.takeIf { it > 0 }
    return Pair(index, total)
}

/**
 * Interprets raw container metadata (ID3v2, MP4, Xiph) into structured AuralisMetadata.
 *
 * Implements strict Auralis rules:
 * - Artists and Album Artists are completely independent (no fallback, no auto "Various Artists").
 * - Multi-value fields are split only by semicolon ';'.
 * - Date is strictly YYYY-MM-DD (no year fallback, no originaldate fallback).
 * - Format-specific genres are decoded to standard strings before semicolon splitting.
 */
fun Metadata.interpret(): AuralisMetadata {
    val isXiph = xiph.isNotEmpty()
    val isMp4 = mp4.isNotEmpty()

    // 1. Title
    val title = when {
        isXiph -> xiph["TITLE"]?.firstOrNull()
        isMp4 -> mp4["©nam"]?.firstOrNull() ?: mp4["©trk"]?.firstOrNull()
        else -> id3v2["TIT2"]?.firstOrNull()
    }?.trim()?.ifBlank { null }

    // 2. Artists (strictly independent, no fallback to album artist / composer)
    val rawArtists: List<String> = when {
        isXiph -> xiph["ARTISTS"] ?: xiph["ARTIST"] ?: emptyList()
        isMp4 -> mp4["----:COM.APPLE.ITUNES:ARTISTS"]
            ?: mp4["©ART"]
            ?: mp4["----:COM.APPLE.ITUNES:ARTIST"]
            ?: emptyList()
        else -> id3v2["TXXX:ARTISTS"]
            ?: id3v2["TPE1"]
            ?: id3v2["TXXX:ARTIST"]
            ?: emptyList()
    }
    val artists = splitSemicolonList(rawArtists)

    // 3. Album Artists (strictly independent, no fallback to artist / composer / "Various Artists")
    val rawAlbumArtists: List<String> = when {
        isXiph -> xiph["ALBUMARTISTS"]
            ?: xiph["ALBUM_ARTISTS"]
            ?: xiph["ALBUM ARTISTS"]
            ?: xiph["ALBUMARTIST"]
            ?: xiph["ALBUM ARTIST"]
            ?: emptyList()
        isMp4 -> mp4["----:COM.APPLE.ITUNES:ALBUMARTISTS"]
            ?: mp4["----:COM.APPLE.ITUNES:ALBUM_ARTISTS"]
            ?: mp4["----:COM.APPLE.ITUNES:ALBUM ARTISTS"]
            ?: mp4["aART"]
            ?: mp4["----:COM.APPLE.ITUNES:ALBUMARTIST"]
            ?: mp4["----:COM.APPLE.ITUNES:ALBUM ARTIST"]
            ?: emptyList()
        else -> id3v2["TXXX:ALBUMARTISTS"]
            ?: id3v2["TXXX:ALBUM_ARTISTS"]
            ?: id3v2["TXXX:ALBUM ARTISTS"]
            ?: id3v2["TPE2"]
            ?: id3v2["TXXX:ALBUMARTIST"]
            ?: id3v2["TXXX:ALBUM ARTIST"]
            ?: emptyList()
    }
    val albumArtists = splitSemicolonList(rawAlbumArtists)

    // 4. Album
    val album = when {
        isXiph -> xiph["ALBUM"]?.firstOrNull()
        isMp4 -> mp4["©alb"]?.firstOrNull()
        else -> id3v2["TALB"]?.firstOrNull()
    }?.trim()?.ifBlank { null }

    // 5. Genres
    val decodedGenres: List<String> = when {
        isXiph -> xiph["GENRE"] ?: emptyList()
        isMp4 -> mp4DecodedGenreValues(mp4)
        else -> {
            val tcon = id3v2["TCON"] ?: emptyList()
            tcon.flatMap { decodeId3Genre(it) }
        }
    }
    val genres = splitSemicolonList(decodedGenres)

    // 6. Date (standard release date only, no ORIGINALDATE / YEAR fallback)
    val rawDate = when {
        isXiph -> xiph["DATE"]?.firstOrNull()
        isMp4 -> mp4["©day"]?.firstOrNull()
        else -> id3v2["TDRC"]?.firstOrNull()
    }
    val date = parseAuralisDate(rawDate)

    // 7. Track position
    val (trackNumber, trackTotal) = when {
        isXiph -> {
            val trackRaw = xiph["TRACKNUMBER"]?.firstOrNull()
            val totalRaw = (xiph["TOTALTRACKS"] ?: xiph["TRACKTOTAL"] ?: xiph["TRACKC"])?.firstOrNull()
            parsePositionWithFallback(trackRaw, totalRaw)
        }
        isMp4 -> parsePositionWithFallback(mp4["trkn"]?.firstOrNull())
        else -> parsePositionWithFallback(id3v2["TRCK"]?.firstOrNull())
    }

    // 8. Disc position
    val (discNumber, discTotal) = when {
        isXiph -> {
            val discRaw = xiph["DISCNUMBER"]?.firstOrNull()
            val totalRaw = (xiph["TOTALDISCS"] ?: xiph["DISCTOTAL"] ?: xiph["DISCC"])?.firstOrNull()
            parsePositionWithFallback(discRaw, totalRaw)
        }
        isMp4 -> parsePositionWithFallback(mp4["disk"]?.firstOrNull())
        else -> parsePositionWithFallback(id3v2["TPOS"]?.firstOrNull())
    }

    // 9. Embedded lyrics
    val embeddedLyrics = when {
        isXiph -> xiph["LYRICS"]?.firstOrNull() ?: xiph["UNSYNCEDLYRICS"]?.firstOrNull()
        isMp4 -> mp4["©lyr"]?.firstOrNull()
        else -> id3v2["USLT"]?.firstOrNull()
            ?: id3v2.entries.firstOrNull { it.key.startsWith("USLT:") }?.value?.firstOrNull()
    }?.takeIf { it.isNotBlank() }

    // 10. Embedded artwork
    val hasEmbeddedArtwork = cover != null && cover.isNotEmpty()
    val artworkBytes = cover?.size ?: 0

    return AuralisMetadata(
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
        artworkData = cover,
        durationMs = properties.durationMs,
        bitrateKbps = properties.bitrateKbps,
        sampleRateHz = properties.sampleRateHz,
        mimeType = properties.mimeType,
        rawMetadata = this,
    )
}
