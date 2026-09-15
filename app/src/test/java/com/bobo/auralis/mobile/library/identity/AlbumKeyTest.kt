package com.bobo.auralis.mobile.library.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `docs/phase3a/01_IDENTITY.md` §5.1 and the AlbumKey rows of the §58 matrix.
 */
class AlbumKeyTest {

    private fun keyOf(
        album: String?,
        albumArtists: List<String> = emptyList(),
        date: String? = null,
    ): AlbumKey? = AlbumIdentity.from(album, albumArtists, date)?.let(AlbumKeyV1::derive)

    @Test
    fun `album key is versioned and deterministic`() {
        val first = keyOf("BRAT", listOf("Charli xcx"), "2024-06-07")
        val second = keyOf("BRAT", listOf("Charli xcx"), "2024-06-07")
        assertEquals(1, first?.version)
        assertEquals(first, second)
        assertEquals(64, first?.hash?.length)
    }

    @Test
    fun `album artist order does not change the album key`() {
        assertEquals(
            keyOf("Album", listOf("A", "B")),
            keyOf("Album", listOf("B", "A")),
        )
    }

    @Test
    fun `album title change changes the album key`() {
        assertNotEquals(keyOf("Album A", listOf("X")), keyOf("Album B", listOf("X")))
    }

    @Test
    fun `album artist change changes the album key`() {
        assertNotEquals(keyOf("Album", listOf("X")), keyOf("Album", listOf("Y")))
    }

    @Test
    fun `date change changes the album key`() {
        assertNotEquals(
            keyOf("Album", listOf("X"), "2025-01-01"),
            keyOf("Album", listOf("X"), "2026-01-01"),
        )
    }

    @Test
    fun `null date and a present date are different albums`() {
        assertNotEquals(keyOf("Album", listOf("X"), null), keyOf("Album", listOf("X"), "2025-01-01"))
    }

    @Test
    fun `missing album artist is not the same as a present album artist`() {
        assertNotEquals(keyOf("Album", emptyList()), keyOf("Album", listOf("X")))
    }

    @Test
    fun `nfc equivalent album artists share an album key`() {
        assertEquals(
            keyOf("Album", listOf("Caf\u00E9")),
            keyOf("Album", listOf("Cafe\u0301")),
        )
    }

    @Test
    fun `missing album title produces no album key`() {
        assertNull(keyOf(null, listOf("X"), "2025-01-01"))
    }

    @Test
    fun `the album title alone never decides identity`() {
        assertNotEquals(
            keyOf("Album", listOf("X"), "2025-01-01"),
            keyOf("Album", listOf("X"), "2026-01-01"),
        )
        assertNotEquals(keyOf("Album", listOf("X")), keyOf("Album", listOf("Y")))
    }
}
