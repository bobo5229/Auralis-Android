package com.bobo.auralis.mobile.library.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `docs/phase3a/01_IDENTITY.md` §11, including the two serialization edge cases
 * §58 requires as dedicated tests.
 */
class IdentityHasherTest {

    private fun hash(block: IdentityHasher.() -> IdentityHasher): String =
        IdentityHasher().marker("AURALIS_TEST_V1").block().hash()

    @Test
    fun `null and empty string do not collide`() {
        val nullHash = hash { field("f").text(null) }
        val emptyHash = hash { field("f").text("") }
        assertNotEquals(nullHash, emptyHash)
    }

    @Test
    fun `list element boundaries do not collide`() {
        val first = hash { field("f").textList(listOf("AB", "C")) }
        val second = hash { field("f").textList(listOf("A", "BC")) }
        assertNotEquals(first, second)
    }

    @Test
    fun `list length is part of the encoding`() {
        assertNotEquals(
            hash { field("f").textList(listOf("A")) },
            hash { field("f").textList(listOf("A", "A")) },
        )
    }

    @Test
    fun `empty list and null text do not collide`() {
        assertNotEquals(
            hash { field("f").textList(emptyList()) },
            hash { field("f").text(null) },
        )
    }

    @Test
    fun `field names keep neighbouring values apart`() {
        assertNotEquals(
            hash { field("x").text("1").field("y").text("2") },
            hash { field("x").text("12").field("y").text(null) },
        )
    }

    @Test
    fun `null integer and zero do not collide`() {
        assertNotEquals(
            hash { field("n").int(null) },
            hash { field("n").int(0) },
        )
    }

    @Test
    fun `different markers produce different hashes`() {
        val album = IdentityHasher().marker("AURALIS_ALBUM_V1").field("t").text("X").hash()
        val track = IdentityHasher().marker("AURALIS_TRACK_V1").field("t").text("X").hash()
        assertNotEquals(album, track)
    }

    @Test
    fun `identical input is deterministic and lower case sha256 hex`() {
        val first = hash { field("t").text("Value") }
        val second = hash { field("t").text("Value") }
        assertEquals(first, second)
        assertEquals(64, first.length)
        assertTrue("expected lowercase hex, got $first", first.all { it in "0123456789abcdef" })
    }
}
