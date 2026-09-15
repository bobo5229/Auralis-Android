package com.bobo.auralis.mobile.library.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `docs/phase3a/01_IDENTITY.md` §3 and §4.
 */
class IdentityTextNormalizerTest {

    @Test
    fun `null stays null`() {
        assertNull(IdentityTextNormalizerV1.normalize(null))
    }

    @Test
    fun `blank values become null`() {
        assertNull(IdentityTextNormalizerV1.normalize(""))
        assertNull(IdentityTextNormalizerV1.normalize("   "))
        assertNull(IdentityTextNormalizerV1.normalize("\t\n "))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("Album", IdentityTextNormalizerV1.normalize("  Album  "))
        assertEquals("Album", IdentityTextNormalizerV1.normalize("\tAlbum\n"))
    }

    @Test
    fun `internal whitespace and punctuation are preserved`() {
        assertEquals("Tyler, the Creator", IdentityTextNormalizerV1.normalize("Tyler, the Creator"))
        assertEquals("R&B/Soul", IdentityTextNormalizerV1.normalize("R&B/Soul"))
        assertEquals("AC/DC", IdentityTextNormalizerV1.normalize("AC/DC"))
        assertEquals("A  B", IdentityTextNormalizerV1.normalize(" A  B "))
    }

    @Test
    fun `case is never folded`() {
        assertNotEquals(
            IdentityTextNormalizerV1.normalize("Taylor Swift"),
            IdentityTextNormalizerV1.normalize("taylor swift"),
        )
    }

    @Test
    fun `nfc equivalent forms normalize to the same text`() {
        val composed = "Caf\u00E9"          // é as a single code point
        val decomposed = "Cafe\u0301"       // e followed by a combining acute accent
        assertNotEquals(composed, decomposed)
        assertEquals(
            IdentityTextNormalizerV1.normalize(composed),
            IdentityTextNormalizerV1.normalize(decomposed),
        )
    }

    @Test
    fun `unicode space separators including non breaking spaces are trimmed`() {
        // Kotlin's Char.isWhitespace() is Char.isWhitespace || isSpaceChar, so
        // Unicode space separators such as U+00A0 are trim candidates too.
        assertEquals("Album", IdentityTextNormalizerV1.normalize("\u00A0Album"))
        assertEquals("A B", IdentityTextNormalizerV1.normalize("\u00A0A B\u00A0"))
        assertEquals("Album", IdentityTextNormalizerV1.normalize("\u3000Album\u3000"))
        assertNull(IdentityTextNormalizerV1.normalize("\u00A0"))
    }

    @Test
    fun `canonical collection drops blanks and deduplicates exactly`() {
        assertEquals(
            listOf("A", "B"),
            IdentityTextNormalizerV1.canonicalCollection(
                listOf("B", "  ", "A", "A", "", "  A  "),
            ),
        )
    }

    @Test
    fun `canonical collection order does not matter`() {
        assertEquals(
            IdentityTextNormalizerV1.canonicalCollection(listOf("A", "B")),
            IdentityTextNormalizerV1.canonicalCollection(listOf("B", "A")),
        )
    }

    @Test
    fun `canonical collection does not case fold or merge near duplicates`() {
        assertEquals(
            listOf("A", "a"),
            IdentityTextNormalizerV1.canonicalCollection(listOf("A", "a")),
        )
    }

    @Test
    fun `canonical collection of only blanks is empty`() {
        assertEquals(
            emptyList<String>(),
            IdentityTextNormalizerV1.canonicalCollection(listOf("", "   ", "\t")),
        )
    }

    @Test
    fun `canonical collection is deterministic across input permutations`() {
        val expected = listOf("Alpha", "Beta", "Gamma")
        assertEquals(
            expected,
            IdentityTextNormalizerV1.canonicalCollection(listOf("Gamma", "Alpha", "Beta")),
        )
        assertEquals(
            expected,
            IdentityTextNormalizerV1.canonicalCollection(listOf("Beta", "Gamma", "Alpha")),
        )
    }
}
