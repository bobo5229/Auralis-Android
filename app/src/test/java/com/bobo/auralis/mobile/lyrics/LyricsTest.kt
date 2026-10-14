package com.bobo.auralis.mobile.lyrics

import org.junit.Assert.*
import org.junit.Test

class LyricsTest {
    @Test fun decodesUtf8AndUtf16WithoutSilentlyReplacingInvalidBytes() {
        assertEquals("歌词", decodeLyrics("歌词".toByteArray(Charsets.UTF_8)))
        assertEquals("歌词", decodeLyrics("歌词".toByteArray(Charsets.UTF_16)))
        assertEquals("歌词", decodeLyrics("\uFEFF歌词".toByteArray(Charsets.UTF_8)))
        assertThrows(java.nio.charset.CharacterCodingException::class.java) {
            decodeLyrics(byteArrayOf(0xFF.toByte()))
        }
    }
    @Test fun plainAndEmptyLyrics() {
        assertNull(parseLyrics(" \n "))
        assertNull(parseLyrics("[ar:Artist]"))
        val lyrics = requireNotNull(parseLyrics("\uFEFF第一行\n第二行"))
        assertEquals("第一行\n第二行", lyrics.plainText)
        assertTrue(lyrics.lines.isEmpty())
    }

    @Test fun timestampsOffsetsRepeatedTagsAndTranslations() {
        val lyrics = requireNotNull(parseLyrics("[offset:-100]\n[00:02.12]Second\n[00:01.5][00:03.005]First\n[00:01.500]翻译\n[00:04]"))
        assertEquals(listOf(1400L, 2020L, 2905L, 3900L), lyrics.lines.map { it.timeMs })
        assertEquals("First\n翻译", lyrics.lines[0].text)
        assertEquals(-1, lyrics.activeIndex(1399))
        assertEquals(0, lyrics.activeIndex(1400))
        assertEquals(2, lyrics.activeIndex(3000))
        assertEquals(3, lyrics.activeIndex(9000))
        assertEquals(0, lyrics.activeIndex(1500))
    }

    @Test fun malformedTimestampsRemainReadableAndOffsetsClampAtZero() {
        val plain = requireNotNull(parseLyrics("[99:99]invalid\n[broken]text"))
        assertTrue(plain.lines.isEmpty())
        assertTrue(plain.plainText.contains("invalid"))
        val lyrics = requireNotNull(parseLyrics("[offset:-5000]\n[00:01]Intro"))
        assertEquals(0L, lyrics.lines.single().timeMs)
    }
}
