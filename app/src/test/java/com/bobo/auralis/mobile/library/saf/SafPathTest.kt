package com.bobo.auralis.mobile.library.saf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafPathTest {
    @Test
    fun parseUnix_splitsAndIgnoresEmptyComponents() {
        val components = SafComponents.parseUnix("/Music//Albums/")
        assertEquals(listOf("Music", "Albums"), components.components)
        assertEquals("Music/Albums", components.unixString)
    }

    @Test
    fun components_supportNavigationAndContainment() {
        val music = SafComponents.parseUnix("Music")
        val album = music.child("Album")
        val track = album.child("track.flac")

        assertEquals("track.flac", track.name)
        assertEquals(album, track.parent())
        assertEquals(music, album.parent())
        assertEquals(SafComponents.root(), music.parent())
        assertEquals(SafComponents.parseUnix("Album/track.flac"), track.depth(1))
        assertTrue(music.contains(track))
        assertTrue(album.contains(track))
        assertEquals(SafComponents.parseUnix("Album/track.flac"), music.containing(track))
    }

    @Test
    fun nestedRootsProduceIdenticalFilePaths() {
        val root = SafPath(SafRoot.Volume("primary"), SafComponents.parseUnix("Music"))
        val nestedRoot = SafPath(SafRoot.Volume("primary"), SafComponents.parseUnix("Music/Albums"))

        val fromRoot = root.file("Albums").file("track.flac")
        val fromNestedRoot = nestedRoot.file("track.flac")

        assertEquals(fromRoot, fromNestedRoot)
        assertEquals("primary/Music/Albums/track.flac", fromRoot.toString())
    }

    @Test
    fun opaqueRootsKeepTheirTreeIdentity() {
        val opaque = SafPath(SafRoot.Opaque("content://provider/tree/root"), SafComponents.root())
        assertEquals("content://provider/tree/root", opaque.toString())
        assertEquals("song.mp3", opaque.file("song.mp3").name)
    }

    @Test
    fun parseTreeDocumentId_recognizesVolumePrefixAndRelativePath() {
        val path = SafTreePathParser.parse("primary:Music/Albums")
        assertEquals(SafRoot.Volume("primary"), path?.root)
        assertEquals(listOf("Music", "Albums"), path?.components?.components)
    }

    @Test
    fun parseTreeDocumentId_keepsColonsInsideRelativePath() {
        val path = SafTreePathParser.parse("primary:Music: Deluxe/Vol 1")
        assertEquals(SafRoot.Volume("primary"), path?.root)
        assertEquals(listOf("Music: Deluxe", "Vol 1"), path?.components?.components)
    }

    @Test
    fun parseTreeDocumentId_acceptsVolumeRootAndOtherVolumeIds() {
        assertEquals(emptyList<String>(), SafTreePathParser.parse("primary:")?.components?.components)
        assertEquals(SafRoot.Volume("1234-5678"), SafTreePathParser.parse("1234-5678:Music")?.root)
    }

    @Test
    fun parseTreeDocumentId_rejectsOpaqueOrMissingPrefixes() {
        assertNull(SafTreePathParser.parse("downloads"))
        assertNull(SafTreePathParser.parse(":Music"))
        assertNull(SafTreePathParser.parse(""))
        assertNull(SafTreePathParser.parse(null))
    }
}
