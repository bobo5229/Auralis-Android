package com.bobo.auralis.mobile.lyrics

import com.bobo.auralis.mobile.library.saf.SafSiblingDocument
import org.junit.Assert.*
import org.junit.Test

class LyricsCandidateTest {
    private fun file(id: String, name: String) = SafSiblingDocument(id, name, "text/plain")

    @Test fun exactCaseWinsRegardlessOfProviderOrder() {
        assertEquals("exact", chooseLyricsCandidate("Song.flac", listOf(
            file("other", "SONG.LRC"), file("exact", "Song.lrc"),
        ))?.id)
    }

    @Test fun fallbackOrderIsStableIncludingDuplicateNames() {
        val files = listOf(file("b", "SONG.LRC"), file("a", "SONG.LRC"), file("c", "song.LRC"))
        assertEquals("a", chooseLyricsCandidate("Song.mp3", files)?.id)
        assertEquals("a", chooseLyricsCandidate("Song.mp3", files.reversed())?.id)
    }

    @Test fun directoriesAndUnrelatedNamesAreExcluded() {
        assertNull(chooseLyricsCandidate("song.mp3", listOf(
            SafSiblingDocument("dir", "song.lrc", "vnd.android.document/directory"),
            file("other", "other.lrc"), file("suffix", "song.lrc.txt"),
        )))
    }

    @Test fun onlyLastExtensionIsRemoved() {
        assertEquals("match", chooseLyricsCandidate("song.live.flac", listOf(file("match", "song.live.lrc")))?.id)
        assertEquals("match", chooseLyricsCandidate("song", listOf(file("match", "song.lrc")))?.id)
    }
}
