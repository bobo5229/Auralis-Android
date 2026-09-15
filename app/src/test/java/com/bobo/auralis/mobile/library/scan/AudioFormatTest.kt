package com.bobo.auralis.mobile.library.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioFormatTest {
    @Test
    fun supportedExtensionsAreRecognized() {
        assertEquals(AudioFormat.MP3, AudioFormat.fromFileName("song.mp3"))
        assertEquals(AudioFormat.M4A, AudioFormat.fromFileName("song.m4a"))
        assertEquals(AudioFormat.AAC, AudioFormat.fromFileName("song.aac"))
        assertEquals(AudioFormat.FLAC, AudioFormat.fromFileName("song.flac"))
    }

    @Test
    fun extensionMatchIsCaseInsensitive() {
        assertEquals(AudioFormat.MP3, AudioFormat.fromFileName("SONG.MP3"))
        assertEquals(AudioFormat.M4A, AudioFormat.fromFileName("Song.M4a"))
        assertEquals(AudioFormat.AAC, AudioFormat.fromFileName("song.AAC"))
        assertEquals(AudioFormat.FLAC, AudioFormat.fromFileName("song.FlAc"))
    }

    @Test
    fun finalExtensionIsUsed() {
        assertNull(AudioFormat.fromFileName("song.mp3.bak"))
        assertEquals(AudioFormat.MP3, AudioFormat.fromFileName("archive.2024.mp3"))
    }

    @Test
    fun unsupportedOrMissingExtensionsAreRejected() {
        assertNull(AudioFormat.fromFileName("song.wav"))
        assertNull(AudioFormat.fromFileName("song.ogg"))
        assertNull(AudioFormat.fromFileName("song"))
        assertNull(AudioFormat.fromFileName("song."))
        assertNull(AudioFormat.fromFileName(""))
    }

    @Test
    fun hiddenFileWithSupportedExtensionIsAccepted() {
        assertEquals(AudioFormat.MP3, AudioFormat.fromFileName(".hidden.mp3"))
    }
}
