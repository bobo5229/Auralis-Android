package com.bobo.auralis.mobile.library.identity

import java.security.MessageDigest

/**
 * Deterministic, length-prefixed encoder feeding a SHA-256 digest.
 *
 * Frozen by `docs/phase3a/01_IDENTITY.md` §11. Naive concatenation such as
 * `"$album|$artist|$date"` is explicitly forbidden because it cannot keep four
 * properties that identity relies on:
 *
 * - `NULL` and `""` stay different values (distinct type tags)
 * - `["A", "BC"]` cannot collide with `["AB", "C"]` (element count + length prefixes)
 * - field boundaries are unambiguous whatever characters a value contains
 * - the encoding is fully deterministic
 *
 * Every variable-length payload is written as a big-endian byte length followed
 * by its UTF-8 bytes, so no value can ever shift the meaning of the next one.
 */
internal class IdentityHasher {

    private val digest = MessageDigest.getInstance("SHA-256")

    /** Leading algorithm marker, for example `AURALIS_TRACK_V1`. */
    fun marker(value: String): IdentityHasher = apply {
        digest.update(TAG_MARKER)
        updateSized(value.toByteArray(Charsets.UTF_8))
    }

    /** Opens a named field so values can never be reordered or confused. */
    fun field(name: String): IdentityHasher = apply {
        digest.update(TAG_FIELD)
        updateSized(name.toByteArray(Charsets.UTF_8))
    }

    /** Writes a nullable text value; null and the empty string encode differently. */
    fun text(value: String?): IdentityHasher = apply {
        if (value == null) {
            digest.update(TAG_NULL)
        } else {
            digest.update(TAG_TEXT)
            updateSized(value.toByteArray(Charsets.UTF_8))
        }
    }

    /** Writes a nullable integer; null and every integer encode differently. */
    fun int(value: Int?): IdentityHasher = apply {
        if (value == null) {
            digest.update(TAG_NULL)
        } else {
            digest.update(TAG_INT)
            updateInt(value)
        }
    }

    /** Writes a list: element count first, then each element with its own length. */
    fun textList(values: List<String>): IdentityHasher = apply {
        digest.update(TAG_LIST)
        updateInt(values.size)
        values.forEach { text(it) }
    }

    /** Final SHA-256 digest as lowercase hex. */
    fun hash(): String = toLowerHex(digest.digest())

    private fun updateInt(value: Int) {
        digest.update((value ushr 24).toByte())
        digest.update((value ushr 16).toByte())
        digest.update((value ushr 8).toByte())
        digest.update(value.toByte())
    }

    private fun updateSized(bytes: ByteArray) {
        updateInt(bytes.size)
        digest.update(bytes)
    }

    private companion object {
        const val TAG_NULL: Byte = 0x00
        const val TAG_TEXT: Byte = 0x01
        const val TAG_INT: Byte = 0x02
        const val TAG_LIST: Byte = 0x03
        const val TAG_MARKER: Byte = 0x10
        const val TAG_FIELD: Byte = 0x11

        val HEX_DIGITS = "0123456789abcdef".toCharArray()

        fun toLowerHex(bytes: ByteArray): String {
            val out = CharArray(bytes.size * 2)
            bytes.forEachIndexed { index, byte ->
                val value = byte.toInt() and 0xFF
                out[index * 2] = HEX_DIGITS[value ushr 4]
                out[index * 2 + 1] = HEX_DIGITS[value and 0x0F]
            }
            return String(out)
        }
    }
}
