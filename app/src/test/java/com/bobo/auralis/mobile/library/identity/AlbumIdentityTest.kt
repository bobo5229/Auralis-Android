package com.bobo.auralis.mobile.library.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `docs/phase3a/01_IDENTITY.md` §5.1 to §5.5.
 */
class AlbumIdentityTest {

    @Test
    fun `missing album title creates no album identity`() {
        assertNull(AlbumIdentity.from(null, listOf("X"), null))
        assertNull(AlbumIdentity.from(null, listOf("X"), "2025-01-01"))
    }

    @Test
    fun `blank album title is treated as missing`() {
        assertNull(AlbumIdentity.from("", emptyList(), null))
        assertNull(AlbumIdentity.from("   ", emptyList(), null))
    }

    @Test
    fun `identity carries title artists and date`() {
        val identity = AlbumIdentity.from("BRAT", listOf("Charli xcx"), "2024-06-07")
        assertEquals("BRAT", identity?.albumTitle)
        assertEquals(listOf("Charli xcx"), identity?.albumArtists)
        assertEquals("2024-06-07", identity?.date)
    }

    @Test
    fun `missing album artist enters the key as the empty set`() {
        val identity = AlbumIdentity.from("Album", emptyList(), null)
        assertEquals(emptyList<String>(), identity?.albumArtists)
    }

    @Test
    fun `album artists are canonicalized`() {
        val identity = AlbumIdentity.from("Album", listOf(" B ", "A", "B"), null)
        assertEquals(listOf("A", "B"), identity?.albumArtists)
    }

    @Test
    fun `missing date stays null rather than being invented`() {
        assertNull(AlbumIdentity.from("Album", listOf("X"), "")?.date)
        assertNull(AlbumIdentity.from("Album", listOf("X"), "   ")?.date)
    }

    @Test
    fun `title and date are trimmed`() {
        val identity = AlbumIdentity.from("  Album  ", listOf("X"), " 2025-01-01 ")
        assertEquals("Album", identity?.albumTitle)
        assertEquals("2025-01-01", identity?.date)
    }

    @Test
    fun `edition suffixes stay distinct albums`() {
        val plain = AlbumIdentity.from("Random Access Memories", listOf("Daft Punk"), null)
        val deluxe =
            AlbumIdentity.from("Random Access Memories (10th Anniversary Edition)", listOf("Daft Punk"), null)
        assertEquals(false, plain == deluxe)
    }
}
