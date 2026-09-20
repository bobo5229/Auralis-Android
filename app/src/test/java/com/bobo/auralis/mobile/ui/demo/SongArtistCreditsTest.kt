package com.bobo.auralis.mobile.ui.demo

import org.junit.Assert.assertEquals
import org.junit.Test

class SongArtistCreditsTest {
    @Test
    fun missingArtistUsesUnknownLabel() {
        assertEquals("未知艺术家", formatSongArtistCredits(emptyList()))
    }

    @Test
    fun singleArtistIsUnchanged() {
        assertEquals("A", formatSongArtistCredits(listOf("A")))
    }

    @Test
    fun twoArtistsUseAmpersand() {
        assertEquals("A & B", formatSongArtistCredits(listOf("A", "B")))
    }

    @Test
    fun threeOrMoreArtistsUseCommasAndFinalAmpersand() {
        assertEquals("A, B & C", formatSongArtistCredits(listOf("A", "B", "C")))
        assertEquals("A, B, C & D", formatSongArtistCredits(listOf("A", "B", "C", "D")))
    }
}
