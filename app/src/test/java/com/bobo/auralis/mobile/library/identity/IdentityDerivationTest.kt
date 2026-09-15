package com.bobo.auralis.mobile.library.identity

import com.bobo.auralis.mobile.library.metadata.AuralisMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * End-to-end identity derivation from interpreted metadata, and the Phase 3A
 * invariants from `docs/phase3a/00_OVERVIEW.md` §73 that identity is responsible
 * for.
 */
class IdentityDerivationTest {

    @Test
    fun `derive fills every identity output`() {
        val derived = IdentityDerivation.derive(
            auralisMetadata(
                title = "360",
                artists = listOf("Charli xcx"),
                albumArtists = listOf("Charli xcx"),
                album = "BRAT",
                date = "2024-06-07",
                trackNumber = 1,
                discNumber = 1,
            ),
        )

        assertEquals("BRAT", derived.albumIdentity?.albumTitle)
        assertEquals(listOf("Charli xcx"), derived.albumIdentity?.albumArtists)
        assertEquals("2024-06-07", derived.albumIdentity?.date)
        assertEquals(1, derived.albumKey?.version)
        assertEquals(1, derived.trackKey.version)
        assertEquals("360", derived.trackIdentity.title)
        assertEquals(listOf("Charli xcx"), derived.trackIdentity.trackArtists)
        assertEquals(1, derived.trackIdentity.trackNumber)
        assertEquals(1, derived.trackIdentity.discNumber)
        assertEquals(TrackKeyStrength.STRONG, derived.trackKeyStrength)
    }

    @Test
    fun `missing album produces no album identity and no album key`() {
        val derived = IdentityDerivation.derive(
            auralisMetadata(title = "Title", artists = listOf("Artist")),
        )
        assertNull(derived.albumIdentity)
        assertNull(derived.albumKey)
        assertNull(derived.trackIdentity.albumIdentity)
        assertEquals(TrackKeyStrength.WEAK, derived.trackKeyStrength)
    }

    @Test
    fun `composer never reaches identity`() {
        // AuralisMetadata carries no composer field by design: METADATA.md forbids
        // falling back from Artist to Composer, so there is nothing to fall back to.
        val fields = declaredFieldNames(AuralisMetadata::class.java)
        assertEquals(false, fields.any { it.contains("composer") })
    }

    @Test
    fun `same metadata derives identical identity`() {
        val metadata = auralisMetadata(
            title = "Title",
            artists = listOf("A", "B"),
            albumArtists = listOf("A"),
            album = "Album",
            date = "2025-01-01",
            trackNumber = 3,
            discNumber = 1,
        )
        assertEquals(IdentityDerivation.derive(metadata), IdentityDerivation.derive(metadata))
    }

    @Test
    fun `multi artist tracks with the same set share a track key`() {
        val forward = auralisMetadata(
            title = "Collab",
            artists = listOf("A", "B"),
            album = "Album",
            trackNumber = 1,
        )
        val shuffled = forward.copy(artists = listOf("B", "A", "A", " "))
        assertEquals(
            IdentityDerivation.derive(forward).trackKey,
            IdentityDerivation.derive(shuffled).trackKey,
        )
    }

    @Test
    fun `same album title with different album artists stays distinct`() {
        val first = auralisMetadata(album = "Greatest Hits", albumArtists = listOf("Queen"))
        val second = auralisMetadata(album = "Greatest Hits", albumArtists = listOf("ABBA"))
        assertNotEquals(
            IdentityDerivation.derive(first).albumKey,
            IdentityDerivation.derive(second).albumKey,
        )
    }

    @Test
    fun `same album and album artist with different dates stays distinct`() {
        val first = auralisMetadata(album = "Album", albumArtists = listOf("X"), date = "2025-01-01")
        val second = auralisMetadata(album = "Album", albumArtists = listOf("X"), date = "2026-01-01")
        assertNotEquals(
            IdentityDerivation.derive(first).albumKey,
            IdentityDerivation.derive(second).albumKey,
        )
    }

    @Test
    fun `track identity excludes every field listed in section 9`() {
        val identity = IdentityDerivation.derive(
            auralisMetadata(
                title = "Title",
                artists = listOf("Artist"),
                album = "Album",
                genres = listOf("Genre"),
                trackNumber = 1,
            ),
        ).trackIdentity

        val names = declaredFieldNames(identity::class.java)
        assertEquals(
            setOf("albumidentity", "title", "trackartists", "discnumber", "tracknumber"),
            names,
        )
    }

    /**
     * Property names of a Kotlin class, ignoring compiler-generated members such
     * as a `Companion` object or the `$stable` marker the Compose plugin adds.
     */
    private fun declaredFieldNames(type: Class<*>): Set<String> = type.declaredFields
        .filterNot {
            it.isSynthetic ||
                java.lang.reflect.Modifier.isStatic(it.modifiers) ||
                it.name.startsWith('$')
        }
        .map { it.name.lowercase() }
        .toSet()
}
