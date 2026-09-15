package com.bobo.auralis.mobile.library.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatDecodersTest {

    @Test
    fun `decodeId3Genre decodes numeric string`() {
        assertEquals(listOf("Rock"), decodeId3Genre("17"))
        assertEquals(listOf("Blues"), decodeId3Genre("0"))
        assertEquals(listOf("Synthpop"), decodeId3Genre("147"))
    }

    @Test
    fun `decodeId3Genre decodes parenthesized id`() {
        assertEquals(listOf("Rock"), decodeId3Genre("(17)"))
        assertEquals(listOf("Rock"), decodeId3Genre("(17)Rock"))
        assertEquals(listOf("Rock", "Disco"), decodeId3Genre("(17)(4)"))
    }

    @Test
    fun `decodeId3Genre decodes CR and RX`() {
        assertEquals(listOf("Cover"), decodeId3Genre("CR"))
        assertEquals(listOf("Remix"), decodeId3Genre("RX"))
        assertEquals(listOf("Cover"), decodeId3Genre("(CR)"))
    }

    @Test
    fun `decodeId3Genre preserves raw strings without modification`() {
        assertEquals(listOf("Tyler, the Creator"), decodeId3Genre("Tyler, the Creator"))
        assertEquals(listOf("R&B/Soul"), decodeId3Genre("R&B/Soul"))
        assertEquals(listOf("Alternative Rock"), decodeId3Genre("Alternative Rock"))
    }

    @Test
    fun `decodeMp4Genre decodes 1-indexed genre codes`() {
        assertEquals("Blues", decodeMp4Genre("1"))
        assertEquals("Rock", decodeMp4Genre("18"))
        assertEquals("999", decodeMp4Genre("999"))
        assertEquals("Pop", decodeMp4Genre("Pop"))
    }

    @Test
    fun `parsePosition parses track and disc numbers`() {
        assertEquals(Position(3, 12), parsePosition("3/12"))
        assertEquals(Position(3, null), parsePosition("3"))
        assertEquals(Position(3, null), parsePosition("3/0"))
        assertEquals(Position(1, 2), parsePosition("1/2"))
        assertNull(parsePosition(""))
        assertNull(parsePosition(null))
        assertNull(parsePosition("invalid"))
    }

    @Test
    fun `toRawDisplay correctly extracts ID3v2 metadata and USLT lyrics`() {
        val metadata = Metadata(
            id3v2 = mapOf(
                "TIT2" to listOf("Song Title"),
                "TPE1" to listOf("Artist A", "Artist B"),
                "TPE2" to listOf("Album Artist"),
                "TALB" to listOf("Great Album"),
                "TCON" to listOf("17"),
                "TDRC" to listOf("2024-05-01"),
                "TRCK" to listOf("2/10"),
                "TPOS" to listOf("1/1"),
                "USLT" to listOf("These are unsynchronized lyrics line 1\nline 2"),
            ),
            xiph = emptyMap(),
            mp4 = emptyMap(),
            cover = byteArrayOf(1, 2, 3, 4),
            properties = Properties(
                mimeType = "audio/mpeg",
                durationMs = 180000L,
                bitrateKbps = 320,
                sampleRateHz = 44100,
            ),
        )

        val display = metadata.toRawDisplay("test.mp3")

        assertEquals("test.mp3", display.fileName)
        assertEquals("Song Title", display.title)
        assertEquals(listOf("Artist A", "Artist B"), display.artistsRaw)
        assertEquals(listOf("Album Artist"), display.albumArtistsRaw)
        assertEquals("Great Album", display.album)
        assertEquals(listOf("17"), display.genresRaw)
        assertEquals(listOf("Rock"), display.genresDecoded)
        assertEquals("2024-05-01", display.date)
        assertEquals("2/10", display.trackRaw)
        assertEquals(Position(2, 10), display.trackPosition)
        assertEquals("1/1", display.discRaw)
        assertEquals(Position(1, 1), display.discPosition)
        assertTrue(display.hasLyrics)
        assertEquals(45, display.lyricsLength)
        assertTrue(display.hasArtwork)
        assertEquals(4, display.artworkBytes)
        assertEquals("audio/mpeg", display.mimeType)
        assertEquals(180000L, display.durationMs)
    }

    @Test
    fun `toRawDisplay correctly extracts MP4 metadata`() {
        val metadata = Metadata(
            id3v2 = emptyMap(),
            xiph = emptyMap(),
            mp4 = mapOf(
                "©nam" to listOf("M4A Title"),
                "©ART" to listOf("Tyler, the Creator"),
                "aART" to listOf("Tyler, the Creator"),
                "©alb" to listOf("IGOR"),
                "gnre" to listOf("18"),
                "©day" to listOf("2019-05-17"),
                "trkn" to listOf("4/12"),
                "disk" to listOf("1/1"),
                "©lyr" to listOf("M4A lyrics text"),
            ),
            cover = byteArrayOf(0xFF.toByte(), 0xD8.toByte()),
            properties = Properties(
                mimeType = "audio/aac",
                durationMs = 210000L,
                bitrateKbps = 256,
                sampleRateHz = 44100,
            ),
        )

        val display = metadata.toRawDisplay("igor.m4a")

        assertEquals("M4A Title", display.title)
        assertEquals(listOf("Tyler, the Creator"), display.artistsRaw)
        assertEquals(listOf("Rock"), display.genresDecoded)
        assertEquals(Position(4, 12), display.trackPosition)
        assertTrue(display.hasLyrics)
        assertEquals(15, display.lyricsLength)
        assertTrue(display.hasArtwork)
        assertEquals(2, display.artworkBytes)
        assertEquals("audio/aac", display.mimeType)
    }

    @Test
    fun `toRawDisplay correctly extracts FLAC metadata`() {
        val metadata = Metadata(
            id3v2 = emptyMap(),
            xiph = mapOf(
                "TITLE" to listOf("FLAC Title"),
                "ARTIST" to listOf("Artist 1", "Artist 2"),
                "ALBUMARTIST" to listOf("Main Artist"),
                "ALBUM" to listOf("FLAC Album"),
                "GENRE" to listOf("R&B/Soul"),
                "DATE" to listOf("2021-11-12"),
                "TRACKNUMBER" to listOf("5"),
                "DISCNUMBER" to listOf("2"),
                "LYRICS" to listOf("FLAC lyrics"),
            ),
            mp4 = emptyMap(),
            cover = null,
            properties = Properties(
                mimeType = "audio/flac",
                durationMs = 240000L,
                bitrateKbps = 900,
                sampleRateHz = 96000,
            ),
        )

        val display = metadata.toRawDisplay("track.flac")

        assertEquals("FLAC Title", display.title)
        assertEquals(listOf("Artist 1", "Artist 2"), display.artistsRaw)
        assertEquals(listOf("Main Artist"), display.albumArtistsRaw)
        assertEquals("FLAC Album", display.album)
        assertEquals(listOf("R&B/Soul"), display.genresRaw)
        assertEquals(listOf("R&B/Soul"), display.genresDecoded)
        assertEquals(Position(5, null), display.trackPosition)
        assertEquals(Position(2, null), display.discPosition)
        assertTrue(display.hasLyrics)
        assertFalse(display.hasArtwork)
        assertEquals(0, display.artworkBytes)
        assertEquals("audio/flac", display.mimeType)
    }
}
