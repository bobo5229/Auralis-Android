package com.bobo.auralis.mobile.library.identity

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The strength table from `docs/phase3a/01_IDENTITY.md` §10.
 */
class TrackKeyStrengthTest {

    private fun strengthOf(
        album: String? = null,
        title: String? = null,
        artists: List<String> = emptyList(),
        trackNumber: Int? = null,
        discNumber: Int? = null,
    ): TrackKeyStrength = IdentityDerivation.derive(
        auralisMetadata(
            album = album,
            title = title,
            artists = artists,
            trackNumber = trackNumber,
            discNumber = discNumber,
        ),
    ).trackKeyStrength

    @Test
    fun `album title and artist is strong`() {
        assertEquals(
            TrackKeyStrength.STRONG,
            strengthOf(album = "Album", title = "Title", artists = listOf("Artist")),
        )
    }

    @Test
    fun `album title and track number is strong`() {
        assertEquals(
            TrackKeyStrength.STRONG,
            strengthOf(album = "Album", title = "Title", trackNumber = 1),
        )
    }

    @Test
    fun `album artist and track number is strong`() {
        assertEquals(
            TrackKeyStrength.STRONG,
            strengthOf(album = "Album", artists = listOf("Artist"), trackNumber = 1),
        )
    }

    @Test
    fun `album and title alone is weak`() {
        assertEquals(TrackKeyStrength.WEAK, strengthOf(album = "Album", title = "Title"))
    }

    @Test
    fun `album and artist alone is weak`() {
        assertEquals(TrackKeyStrength.WEAK, strengthOf(album = "Album", artists = listOf("Artist")))
    }

    @Test
    fun `album and track number alone is weak`() {
        assertEquals(TrackKeyStrength.WEAK, strengthOf(album = "Album", trackNumber = 1))
    }

    @Test
    fun `title and artist without album is weak`() {
        assertEquals(
            TrackKeyStrength.WEAK,
            strengthOf(title = "Title", artists = listOf("Artist")),
        )
    }

    @Test
    fun `everything missing is weak`() {
        assertEquals(TrackKeyStrength.WEAK, strengthOf())
    }

    @Test
    fun `disc number never contributes to strength by itself`() {
        assertEquals(TrackKeyStrength.WEAK, strengthOf(album = "Album", discNumber = 1))
        assertEquals(TrackKeyStrength.WEAK, strengthOf(album = "Album", title = "Title", discNumber = 1))
    }

    @Test
    fun `blank values count as absent evidence`() {
        assertEquals(
            TrackKeyStrength.WEAK,
            strengthOf(album = "Album", title = "   ", artists = listOf("  ")),
        )
    }
}
