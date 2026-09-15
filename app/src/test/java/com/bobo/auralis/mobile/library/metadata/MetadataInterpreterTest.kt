package com.bobo.auralis.mobile.library.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataInterpreterTest {

    private val defaultProperties = Properties(
        durationMs = 180000L,
        bitrateKbps = 320,
        sampleRateHz = 44100,
        mimeType = "audio/mpeg"
    )

    private fun createMetadata(
        id3v2: Map<String, List<String>> = emptyMap(),
        mp4: Map<String, List<String>> = emptyMap(),
        xiph: Map<String, List<String>> = emptyMap(),
        cover: ByteArray? = null,
        properties: Properties = defaultProperties,
    ) = Metadata(
        id3v2 = id3v2,
        mp4 = mp4,
        xiph = xiph,
        cover = cover,
        properties = properties,
    )

    // 1. Semicolon splitting tests
    @Test
    fun testSemicolonSplittingBasic() {
        val result = splitSemicolonList(listOf("A; B; C"))
        assertEquals(listOf("A", "B", "C"), result)
    }

    @Test
    fun testTylerTheCreatorRemainsSingleArtist() {
        val result = splitSemicolonList(listOf("Tyler, the Creator"))
        assertEquals(listOf("Tyler, the Creator"), result)
    }

    @Test
    fun testGenresWithSlashesRemainSingleGenre() {
        val rb = splitSemicolonList(listOf("R&B/Soul"))
        assertEquals(listOf("R&B/Soul"), rb)

        val hiphop = splitSemicolonList(listOf("Hip-hop/Rap"))
        assertEquals(listOf("Hip-hop/Rap"), hiphop)
    }

    @Test
    fun testAmpersandAndFeatRemainSingleArtist() {
        val andArtist = splitSemicolonList(listOf("A & B"))
        assertEquals(listOf("A & B"), andArtist)

        val featArtist = splitSemicolonList(listOf("A feat. B"))
        assertEquals(listOf("A feat. B"), featArtist)

        val ftArtist = splitSemicolonList(listOf("A ft. B"))
        assertEquals(listOf("A ft. B"), ftArtist)

        val xArtist = splitSemicolonList(listOf("A x B"))
        assertEquals(listOf("A x B"), xArtist)
    }

    @Test
    fun testSemicolonLeadingTrailingSpacesTrimmed() {
        val result = splitSemicolonList(listOf("  A   ;   B ;  C   "))
        assertEquals(listOf("A", "B", "C"), result)
    }

    @Test
    fun testConsecutiveAndEmptySemicolonsDoNotProduceEmptyEntities() {
        val result = splitSemicolonList(listOf(";;A;;;B;;"))
        assertEquals(listOf("A", "B"), result)

        val empty = splitSemicolonList(listOf("   ; ;  ;; "))
        assertTrue(empty.isEmpty())
    }

    @Test
    fun testPhysicalListCombinedWithSemicolons() {
        val result = splitSemicolonList(listOf("周杰伦; 林俊杰", "陶喆", "陈奕迅; 王力宏"))
        assertEquals(listOf("周杰伦", "林俊杰", "陶喆", "陈奕迅", "王力宏"), result)
    }

    // 2. Artist vs Album Artist strict independence
    @Test
    fun testArtistAndAlbumArtistDoNotFallbackToEachOther() {
        // Case 1: Has artist, missing album artist
        val meta1 = createMetadata(
            id3v2 = mapOf("TPE1" to listOf("Artist One"), "TALB" to listOf("Some Album"))
        ).interpret()
        assertEquals(listOf("Artist One"), meta1.artists)
        assertTrue("Album artist must be empty list when missing", meta1.albumArtists.isEmpty())

        // Case 2: Has album artist, missing artist
        val meta2 = createMetadata(
            id3v2 = mapOf("TPE2" to listOf("Album Artist One"), "TALB" to listOf("Some Album"))
        ).interpret()
        assertTrue("Artist must be empty list when missing", meta2.artists.isEmpty())
        assertEquals(listOf("Album Artist One"), meta2.albumArtists)
    }

    @Test
    fun testMissingArtistDoesNotFallbackToComposerOrVariousArtists() {
        val meta = createMetadata(
            id3v2 = mapOf(
                "TALB" to listOf("Soundtrack Album"),
                "TCOM" to listOf("Hans Zimmer"),
                "TCMP" to listOf("1") // Compilation flag
            )
        ).interpret()

        assertTrue("Artist must remain empty even if Composer is present", meta.artists.isEmpty())
        assertTrue("Album artist must remain empty and not auto-populate Various Artists", meta.albumArtists.isEmpty())
    }

    // 3. Date rules
    @Test
    fun testCompleteYyyyMmDdPreservedAsIs() {
        val meta = createMetadata(
            id3v2 = mapOf("TDRC" to listOf("2024-05-20"))
        ).interpret()
        assertEquals("2024-05-20", meta.date)
    }

    @Test
    fun testIso8601WithTimeExtractsCanonicalDate() {
        val meta = createMetadata(
            xiph = mapOf("DATE" to listOf("2024-05-20T14:30:00Z"))
        ).interpret()
        assertEquals("2024-05-20", meta.date)

        val metaSpace = createMetadata(
            mp4 = mapOf("©day" to listOf("2024-05-20 18:00:00"))
        ).interpret()
        assertEquals("2024-05-20", metaSpace.date)
    }

    @Test
    fun testYearOnlyOrInvalidDateReturnsNull() {
        // Year only: do not substitute Date
        val metaYearOnly = createMetadata(
            id3v2 = mapOf("TDRC" to listOf("2024"))
        ).interpret()
        assertNull("Year-only must not be used as Date", metaYearOnly.date)

        // Non-standard formats must not be guessing-repaired
        val metaInvalid = createMetadata(
            xiph = mapOf("DATE" to listOf("05/20/2024"))
        ).interpret()
        assertNull(metaInvalid.date)

        val metaYearMonth = createMetadata(
            mp4 = mapOf("©day" to listOf("2024-05"))
        ).interpret()
        assertNull(metaYearMonth.date)
    }

    // 4. Track and Disc position parsing
    @Test
    fun testTrackAndDiscWithSlash() {
        val meta = createMetadata(
            id3v2 = mapOf(
                "TRCK" to listOf("1/12"),
                "TPOS" to listOf("2/3")
            )
        ).interpret()
        assertEquals(1, meta.trackNumber)
        assertEquals(12, meta.trackTotal)
        assertEquals(2, meta.discNumber)
        assertEquals(3, meta.discTotal)
    }

    @Test
    fun testTrackNumberWithoutTotal() {
        val meta = createMetadata(
            mp4 = mapOf(
                "trkn" to listOf("5"),
                "disk" to listOf("1")
            )
        ).interpret()
        assertEquals(5, meta.trackNumber)
        assertNull(meta.trackTotal)
        assertEquals(1, meta.discNumber)
        assertNull(meta.discTotal)
    }

    @Test
    fun testXiphSeparateTotalFields() {
        val meta = createMetadata(
            xiph = mapOf(
                "TRACKNUMBER" to listOf("3"),
                "TOTALTRACKS" to listOf("10"),
                "DISCNUMBER" to listOf("1"),
                "DISCTOTAL" to listOf("2")
            )
        ).interpret()
        assertEquals(3, meta.trackNumber)
        assertEquals(10, meta.trackTotal)
        assertEquals(1, meta.discNumber)
        assertEquals(2, meta.discTotal)
    }

    @Test
    fun testIllegalTrackAndDiscDoNotCrash() {
        val meta = createMetadata(
            id3v2 = mapOf(
                "TRCK" to listOf("abc/def"),
                "TPOS" to listOf("-1/0")
            )
        ).interpret()
        assertNull(meta.trackNumber)
        assertNull(meta.trackTotal)
        assertNull(meta.discNumber)
        assertNull(meta.discTotal)
    }

    // 5. Genre decoding and multi-value handling
    @Test
    fun testId3GenreNumericAndSemicolon() {
        val meta = createMetadata(
            id3v2 = mapOf("TCON" to listOf("17", "Pop; Electronic"))
        ).interpret()
        // 17 -> "Rock", "Pop; Electronic" -> "Pop", "Electronic"
        assertEquals(listOf("Rock", "Pop", "Electronic"), meta.genres)
    }

    @Test
    fun testMp4GnreNumericDecoding() {
        val meta = createMetadata(
            mp4 = mapOf("gnre" to listOf("18")) // 18 in 1-indexed table is Rock (index 17)
        ).interpret()
        assertEquals(listOf("Rock"), meta.genres)
    }

    // 5b. MP4 genre alias mapping (iTunes free-form atom)
    //
    // The key is the uppercase spelling: NativeTagMap.addCombined() uppercases the atom
    // description, so this is what the raw MP4 map actually contains for
    // `----` / mean `com.apple.iTunes` / name `GENRE`.
    private val freeformGenreKey = "----:COM.APPLE.ITUNES:GENRE"

    @Test
    fun testMp4FreeformGenreSingleValue() {
        val meta = createMetadata(
            mp4 = mapOf(freeformGenreKey to listOf("Hyperpop"))
        ).interpret()
        assertEquals(listOf("Hyperpop"), meta.genres)
    }

    /** The shape of a real file: several values stored as several same-named atoms. */
    @Test
    fun testMp4FreeformGenreKeepsEveryPhysicalValue() {
        val meta = createMetadata(
            mp4 = mapOf(freeformGenreKey to listOf("Hyperpop", "Electropop"))
        ).interpret()
        assertEquals(listOf("Hyperpop", "Electropop"), meta.genres)
    }

    @Test
    fun testMp4FreeformGenreSplitsOnSemicolonInsideOneValue() {
        val meta = createMetadata(
            mp4 = mapOf(freeformGenreKey to listOf("Hyperpop; Electropop"))
        ).interpret()
        assertEquals(listOf("Hyperpop", "Electropop"), meta.genres)
    }

    @Test
    fun testMp4FreeformGenreDoesNotSplitOnSlashOrAmpersand() {
        val slash = createMetadata(
            mp4 = mapOf(freeformGenreKey to listOf("R&B/Soul"))
        ).interpret()
        assertEquals(listOf("R&B/Soul"), slash.genres)

        val ampersand = createMetadata(
            mp4 = mapOf(freeformGenreKey to listOf("Hip-hop/Rap", "Drum & Bass"))
        ).interpret()
        assertEquals(listOf("Hip-hop/Rap", "Drum & Bass"), ampersand.genres)
    }

    @Test
    fun testMp4StandardGenreAtomStillWorks() {
        val meta = createMetadata(
            mp4 = mapOf("©gen" to listOf("Rock"))
        ).interpret()
        assertEquals(listOf("Rock"), meta.genres)
    }

    /** A file carrying several genre aliases must not have its values aggregated twice. */
    @Test
    fun testMp4GenreAliasPriorityDoesNotAggregateAcrossAliases() {
        val standardWins = createMetadata(
            mp4 = mapOf(
                "©gen" to listOf("Rock"),
                freeformGenreKey to listOf("Hyperpop", "Electropop"),
                "gnre" to listOf("18"),
            )
        ).interpret()
        assertEquals(listOf("Rock"), standardWins.genres)

        val freeformBeatsGnre = createMetadata(
            mp4 = mapOf(
                freeformGenreKey to listOf("Hyperpop", "Electropop"),
                "gnre" to listOf("18"),
            )
        ).interpret()
        assertEquals(listOf("Hyperpop", "Electropop"), freeformBeatsGnre.genres)
    }

    // 6. Lyrics and Artwork
    @Test
    fun testEmbeddedLyricsAndArtworkPresence() {
        val fakeCover = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val meta = createMetadata(
            id3v2 = mapOf("USLT" to listOf("These are unsynced lyrics")),
            cover = fakeCover
        ).interpret()

        assertEquals("These are unsynced lyrics", meta.embeddedLyrics)
        assertTrue(meta.hasEmbeddedArtwork)
        assertEquals(4, meta.artworkBytes)
        assertEquals(fakeCover, meta.artworkData)
    }

    // 7. Equivalence fixture across MP3 / MP4 / Xiph
    @Test
    fun testEquivalentTagFixturesProduceConsistentAuralisMetadata() {
        val mp3 = createMetadata(
            id3v2 = mapOf(
                "TIT2" to listOf("Song Title"),
                "TPE1" to listOf("Artist A; Artist B"),
                "TPE2" to listOf("Album Artist Main"),
                "TALB" to listOf("Greatest Hits"),
                "TCON" to listOf("Rock"),
                "TDRC" to listOf("2023-11-15"),
                "TRCK" to listOf("4/10"),
                "TPOS" to listOf("1/2"),
                "USLT" to listOf("Lyrics here")
            ),
            properties = Properties("audio/mpeg", 200000, 320, 44100)
        ).interpret()

        val mp4 = createMetadata(
            mp4 = mapOf(
                "©nam" to listOf("Song Title"),
                "©ART" to listOf("Artist A; Artist B"),
                "aART" to listOf("Album Artist Main"),
                "©alb" to listOf("Greatest Hits"),
                "©gen" to listOf("Rock"),
                "©day" to listOf("2023-11-15"),
                "trkn" to listOf("4/10"),
                "disk" to listOf("1/2"),
                "©lyr" to listOf("Lyrics here")
            ),
            properties = Properties("audio/aac", 200000, 256, 44100)
        ).interpret()

        val flac = createMetadata(
            xiph = mapOf(
                "TITLE" to listOf("Song Title"),
                "ARTIST" to listOf("Artist A; Artist B"),
                "ALBUMARTIST" to listOf("Album Artist Main"),
                "ALBUM" to listOf("Greatest Hits"),
                "GENRE" to listOf("Rock"),
                "DATE" to listOf("2023-11-15"),
                "TRACKNUMBER" to listOf("4/10"),
                "DISCNUMBER" to listOf("1/2"),
                "LYRICS" to listOf("Lyrics here")
            ),
            properties = Properties("audio/flac", 200000, 1000, 44100)
        ).interpret()

        // Core fields should be identically interpreted
        assertEquals(mp3.title, mp4.title)
        assertEquals(mp4.title, flac.title)

        assertEquals(mp3.artists, mp4.artists)
        assertEquals(mp4.artists, flac.artists)
        assertEquals(listOf("Artist A", "Artist B"), mp3.artists)

        assertEquals(mp3.albumArtists, mp4.albumArtists)
        assertEquals(mp4.albumArtists, flac.albumArtists)
        assertEquals(listOf("Album Artist Main"), mp3.albumArtists)

        assertEquals(mp3.album, mp4.album)
        assertEquals(mp4.album, flac.album)

        assertEquals(mp3.genres, mp4.genres)
        assertEquals(mp4.genres, flac.genres)

        assertEquals(mp3.date, mp4.date)
        assertEquals(mp4.date, flac.date)
        assertEquals("2023-11-15", mp3.date)

        assertEquals(mp3.trackNumber, mp4.trackNumber)
        assertEquals(mp4.trackNumber, flac.trackNumber)
        assertEquals(4, mp3.trackNumber)

        assertEquals(mp3.trackTotal, mp4.trackTotal)
        assertEquals(mp4.trackTotal, flac.trackTotal)
        assertEquals(10, mp3.trackTotal)

        assertEquals(mp3.discNumber, mp4.discNumber)
        assertEquals(mp4.discNumber, flac.discNumber)
        assertEquals(1, mp3.discNumber)

        assertEquals(mp3.discTotal, mp4.discTotal)
        assertEquals(mp4.discTotal, flac.discTotal)
        assertEquals(2, mp3.discTotal)

        assertEquals(mp3.embeddedLyrics, mp4.embeddedLyrics)
        assertEquals(mp4.embeddedLyrics, flac.embeddedLyrics)
    }
}
