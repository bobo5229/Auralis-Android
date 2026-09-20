package com.bobo.auralis.mobile.artwork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryArtworkResolverTest {
    @Test
    fun `lowercase cover wins over uppercase cover regardless of query order`() {
        val chosen = chooseCoverCandidate(
            listOf(
                CoverCandidate("upper", "COVER.png", "image/png"),
                CoverCandidate("lower", "cover.jpg", "image/jpeg"),
            ),
        )

        assertEquals("lower", chosen?.documentId)
    }

    @Test
    fun `mixed case names and unrelated images are not cover candidates`() {
        val chosen = chooseCoverCandidate(
            listOf(
                CoverCandidate("mixed", "Cover.jpg", "image/jpeg"),
                CoverCandidate("folder", "folder.jpg", "image/jpeg"),
            ),
        )

        assertNull(chosen)
    }

    @Test
    fun `all lowercase candidates are ordered before uppercase fallback candidates`() {
        assertEquals(
            listOf("cover.jpeg", "cover.png", "COVER.jpg"),
            orderCoverCandidates(
                listOf(
                    CoverCandidate("upper", "COVER.jpg", "image/jpeg"),
                    CoverCandidate("png", "cover.png", "image/png"),
                    CoverCandidate("jpeg", "cover.jpeg", "image/jpeg"),
                ),
            ).map { it.fileName },
        )
    }

    @Test
    fun `relative audio path resolves to directories below selected tree`() {
        assertEquals(
            listOf("Artist", "Album"),
            siblingDirectorySegments(
                rootPath = "primary/Music",
                relativePath = "primary/Music/Artist/Album/song.flac",
            ),
        )
    }

    @Test
    fun `audio directly under selected tree has no directory traversal`() {
        assertEquals(
            emptyList<String>(),
            siblingDirectorySegments(
                rootPath = "primary/Music",
                relativePath = "primary/Music/song.mp3",
            ),
        )
    }
}
