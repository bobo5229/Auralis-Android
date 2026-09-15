package com.bobo.auralis.mobile.library.metadata

/**
 * Structural position parsed from track or disc strings like "3/12" or "3".
 * This is format structural parsing, completely separate from Auralis semicolon delimiter rules.
 */
data class Position(val index: Int, val total: Int?) {
    override fun toString(): String = if (total != null) "$index/$total" else "$index"
}

fun parsePosition(raw: String?): Position? {
    if (raw.isNullOrBlank()) return null
    val parts = raw.split('/')
    val index = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
    val total = parts.getOrNull(1)?.trim()?.toIntOrNull()?.takeIf { it > 0 }
    return Position(index, total)
}

/**
 * Standard ID3v1 / Winamp genre table (0 to 147).
 */
val ID3_GENRE_TABLE = arrayOf(
    // ID3 Standard (0-79)
    "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge", "Hip-Hop",
    "Jazz", "Metal", "New Age", "Oldies", "Other", "Pop", "R&B", "Rap",
    "Reggae", "Rock", "Techno", "Industrial", "Alternative", "Ska", "Death Metal", "Pranks",
    "Soundtrack", "Euro-Techno", "Ambient", "Trip-Hop", "Vocal", "Jazz+Funk", "Fusion", "Trance",
    "Classical", "Instrumental", "Acid", "House", "Game", "Sound Clip", "Gospel", "Noise",
    "AlternRock", "Bass", "Soul", "Punk", "Space", "Meditative", "Instrumental Pop", "Instrumental Rock",
    "Ethnic", "Gothic", "Darkwave", "Techno-Industrial", "Electronic", "Pop-Folk", "Eurodance", "Dream",
    "Southern Rock", "Comedy", "Cult", "Gangsta", "Top 40", "Christian Rap", "Pop/Funk", "Jungle",
    "Native American", "Cabaret", "New Wave", "Psychadelic", "Rave", "Showtunes", "Trailer", "Lo-Fi",
    "Tribal", "Acid Punk", "Acid Jazz", "Polka", "Retro", "Musical", "Rock & Roll", "Hard Rock",
    // Winamp Extensions (80-147)
    "Folk", "Folk-Rock", "National Folk", "Swing", "Fast Fusion", "Bebob", "Latin", "Revival",
    "Celtic", "Bluegrass", "Avantgarde", "Gothic Rock", "Progressive Rock", "Psychedelic Rock", "Symphonic Rock", "Slow Rock",
    "Big Band", "Chorus", "Easy Listening", "Acoustic", "Humour", "Speech", "Chanson", "Opera",
    "Chamber Music", "Sonata", "Symphony", "Booty Bass", "Primus", "Porn Groove", "Satire", "Slow Jam",
    "Club", "Tango", "Samba", "Folklore", "Ballad", "Power Ballad", "Rhythmic Soul", "Freestyle",
    "Duet", "Punk Rock", "Drum Solo", "A capella", "Euro-House", "Dance Hall", "Goa", "Drum & Bass",
    "Club-House", "Hardcore", "Terror", "Indie", "Britpop", "Negerpunk", "Polsk Punk", "Beat",
    "Christian Gangsta", "Heavy Metal", "Black Metal", "Crossover", "Contemporary Christian", "Christian Rock", "Merengue", "Salsa",
    "Thrash Metal", "Anime", "JPop", "Synthpop"
)

private val id3GenreRegex = Regex("""((?:\((\d+|RX|CR)\))*)(.+)?""")

/**
 * Decodes standard numeric ID3 TCON format (e.g. "17", "(17)", "(17)Rock", "CR", "RX").
 * This is format standard decoding, not metadata inference.
 */
fun decodeId3Genre(raw: String): List<String> {
    val trimmed = raw.trim()
    val intVal = trimmed.toIntOrNull()
    if (intVal != null) {
        val mapped = ID3_GENRE_TABLE.getOrNull(intVal)
        if (mapped != null) return listOf(mapped)
        return listOf(raw)
    }
    if (trimmed.equals("CR", ignoreCase = true)) return listOf("Cover")
    if (trimmed.equals("RX", ignoreCase = true)) return listOf("Remix")

    val match = id3GenreRegex.matchEntire(trimmed) ?: return listOf(raw)
    val idGroup = match.groupValues.getOrNull(1)
    val nameGroup = match.groupValues.getOrNull(3)

    val results = mutableListOf<String>()
    if (!idGroup.isNullOrEmpty()) {
        val ids = idGroup.removePrefix("(").removeSuffix(")").split(")(")
        for (id in ids) {
            when (id.uppercase()) {
                "CR" -> results.add("Cover")
                "RX" -> results.add("Remix")
                else -> {
                    val idx = id.toIntOrNull()
                    if (idx != null && idx in ID3_GENRE_TABLE.indices) {
                        results.add(ID3_GENRE_TABLE[idx])
                    } else {
                        results.add(id)
                    }
                }
            }
        }
    }
    if (!nameGroup.isNullOrEmpty()) {
        val name = if (nameGroup.startsWith("((")) nameGroup.substring(1) else nameGroup
        if (results.isEmpty() || !results.contains(name)) {
            results.add(name)
        }
    }
    return if (results.isEmpty()) listOf(raw) else results
}

/**
 * Decodes standard MP4 gnre integer box (1-indexed into standard ID3 genre table).
 */
fun decodeMp4Genre(raw: String): String {
    val trimmed = raw.trim()
    val intVal = trimmed.toIntOrNull() ?: return raw
    if (intVal >= 1 && (intVal - 1) in ID3_GENRE_TABLE.indices) {
        return ID3_GENRE_TABLE[intVal - 1]
    }
    return raw
}

/**
 * MP4 atoms that carry a genre, in Auralis alias priority order.
 *
 * Beyond the standard `©gen` box, iTunes and the tools around it write the genre to a free-form
 * atom instead. `NativeTagMap.addCombined()` uppercases the atom description, so the key in the raw
 * MP4 map is this uppercase spelling, not the `com.apple.iTunes` spelling that appears in the file.
 */
private const val MP4_GENRE_ALIAS = "\u00A9gen"
private const val MP4_FREEFORM_GENRE_ALIAS = "----:COM.APPLE.ITUNES:GENRE"
private const val MP4_GNRE_ALIAS = "gnre"

/**
 * The genre alias used for an MP4 tag map: the first alias that has values.
 *
 * Only one alias is used, so a file that happens to carry several genre aliases is not aggregated
 * twice.
 */
private fun mp4GenreAlias(mp4: Map<String, List<String>>): Pair<String, List<String>>? =
    listOf(MP4_GENRE_ALIAS, MP4_FREEFORM_GENRE_ALIAS, MP4_GNRE_ALIAS).firstNotNullOfOrNull { alias ->
        mp4[alias]?.takeIf { it.isNotEmpty() }?.let { alias to it }
    }

/**
 * Physical genre values of the MP4 map's genre alias, exactly as stored: one entry per atom value,
 * in file order, with no splitting, trimming, de-duplication or case folding.
 */
fun mp4RawGenreValues(mp4: Map<String, List<String>>): List<String> =
    mp4GenreAlias(mp4)?.second ?: emptyList()

/**
 * [mp4RawGenreValues] with the numeric `gnre` box decoded through the standard genre table.
 *
 * The textual aliases are returned as written; they are never decoded or split here.
 */
fun mp4DecodedGenreValues(mp4: Map<String, List<String>>): List<String> {
    val alias = mp4GenreAlias(mp4) ?: return emptyList()
    return if (alias.first == MP4_GNRE_ALIAS) alias.second.map { decodeMp4Genre(it) } else alias.second
}

/**
 * Display model for Phase 2B debugging screen, presenting raw metadata extracted faithfully.
 */
data class RawMetadataDisplay(
    val fileName: String,
    val title: String?,
    val artistsRaw: List<String>,
    val albumArtistsRaw: List<String>,
    val album: String?,
    val genresRaw: List<String>,
    val genresDecoded: List<String>,
    val date: String?,
    val trackRaw: String?,
    val trackPosition: Position?,
    val discRaw: String?,
    val discPosition: Position?,
    val hasLyrics: Boolean,
    val lyricsLength: Int,
    val lyricsSnippet: String?,
    val hasArtwork: Boolean,
    val artworkBytes: Int,
    val durationMs: Long,
    val bitrateKbps: Int,
    val sampleRateHz: Int,
    val mimeType: String,
    val id3v2Map: Map<String, List<String>>,
    val mp4Map: Map<String, List<String>>,
    val xiphMap: Map<String, List<String>>,
)

fun Metadata.toRawDisplay(fileName: String): RawMetadataDisplay {
    val isMp4 = mp4.isNotEmpty()
    val isXiph = xiph.isNotEmpty()

    val title = when {
        isXiph -> xiph["TITLE"]?.firstOrNull()
        isMp4 -> mp4["©nam"]?.firstOrNull()
        else -> id3v2["TIT2"]?.firstOrNull()
    }

    val artistsRaw = when {
        isXiph -> xiph["ARTIST"] ?: emptyList()
        isMp4 -> mp4["©ART"] ?: emptyList()
        else -> id3v2["TPE1"] ?: emptyList()
    }

    val albumArtistsRaw = when {
        isXiph -> xiph["ALBUMARTIST"] ?: xiph["ALBUM ARTIST"] ?: emptyList()
        isMp4 -> mp4["aART"] ?: emptyList()
        else -> id3v2["TPE2"] ?: emptyList()
    }

    val album = when {
        isXiph -> xiph["ALBUM"]?.firstOrNull()
        isMp4 -> mp4["©alb"]?.firstOrNull()
        else -> id3v2["TALB"]?.firstOrNull()
    }

    val genresRaw: List<String>
    val genresDecoded: List<String>
    when {
        isXiph -> {
            genresRaw = xiph["GENRE"] ?: emptyList()
            genresDecoded = genresRaw
        }
        isMp4 -> {
            genresRaw = mp4RawGenreValues(mp4)
            genresDecoded = mp4DecodedGenreValues(mp4)
        }
        else -> {
            genresRaw = id3v2["TCON"] ?: emptyList()
            genresDecoded = genresRaw.flatMap { decodeId3Genre(it) }
        }
    }

    val date = when {
        isXiph -> xiph["DATE"]?.firstOrNull()
        isMp4 -> mp4["©day"]?.firstOrNull()
        else -> id3v2["TDRC"]?.firstOrNull() ?: id3v2["TYER"]?.firstOrNull()
    }

    val trackRaw = when {
        isXiph -> xiph["TRACKNUMBER"]?.firstOrNull()
        isMp4 -> mp4["trkn"]?.firstOrNull()
        else -> id3v2["TRCK"]?.firstOrNull()
    }
    val trackPosition = parsePosition(trackRaw)

    val discRaw = when {
        isXiph -> xiph["DISCNUMBER"]?.firstOrNull()
        isMp4 -> mp4["disk"]?.firstOrNull()
        else -> id3v2["TPOS"]?.firstOrNull()
    }
    val discPosition = parsePosition(discRaw)

    val lyrics = when {
        isXiph -> xiph["LYRICS"]?.firstOrNull() ?: xiph["UNSYNCEDLYRICS"]?.firstOrNull()
        isMp4 -> mp4["©lyr"]?.firstOrNull()
        else -> id3v2["USLT"]?.firstOrNull()
            ?: id3v2.entries.firstOrNull { it.key.startsWith("USLT:") }?.value?.firstOrNull()
    }
    val hasLyrics = !lyrics.isNullOrEmpty()
    val lyricsLength = lyrics?.length ?: 0
    val lyricsSnippet = lyrics?.take(100)?.replace("\n", " ")

    val hasArtwork = cover != null && cover.isNotEmpty()
    val artworkBytes = cover?.size ?: 0

    return RawMetadataDisplay(
        fileName = fileName,
        title = title,
        artistsRaw = artistsRaw,
        albumArtistsRaw = albumArtistsRaw,
        album = album,
        genresRaw = genresRaw,
        genresDecoded = genresDecoded,
        date = date,
        trackRaw = trackRaw,
        trackPosition = trackPosition,
        discRaw = discRaw,
        discPosition = discPosition,
        hasLyrics = hasLyrics,
        lyricsLength = lyricsLength,
        lyricsSnippet = lyricsSnippet,
        hasArtwork = hasArtwork,
        artworkBytes = artworkBytes,
        durationMs = properties.durationMs,
        bitrateKbps = properties.bitrateKbps,
        sampleRateHz = properties.sampleRateHz,
        mimeType = properties.mimeType,
        id3v2Map = id3v2,
        mp4Map = mp4,
        xiphMap = xiph,
    )
}
