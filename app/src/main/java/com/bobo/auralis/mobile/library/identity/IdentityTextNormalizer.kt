package com.bobo.auralis.mobile.library.identity

import java.text.Normalizer

/**
 * Auralis identity canonicalization, version 1.
 *
 * Frozen by `docs/phase3a/01_IDENTITY.md` §3 (single text values) and §4
 * (multi-value fields). Identity is deliberately conservative: values that
 * differ only by case, punctuation or a non-canonical Unicode composition stay
 * distinct, because wrongly merging two different works costs more than
 * occasionally keeping two entries.
 *
 * Pure Kotlin: no Context, ContentResolver, Room or Uri dependency.
 */
object IdentityTextNormalizerV1 {

    /**
     * Identity canonicalization for a single text value, in exactly this order:
     *
     * 1. a null value stays null
     * 2. trim leading and trailing Unicode whitespace
     * 3. Unicode normalize to NFC
     * 4. a value that is empty after trimming becomes null
     * 5. every other character is preserved verbatim
     *
     * Explicitly NOT performed: lowercasing, case folding, collapsing internal
     * whitespace, punctuation removal, accent removal, full/half width folding,
     * language conversion, artist aliasing or fuzzy matching.
     *
     * So `Taylor Swift` and `taylor swift` are not the same identity text, and
     * `AC/DC` is never rewritten.
     *
     * Step 2 uses Kotlin's `Char.isWhitespace()`, which is Java's
     * `Character.isWhitespace(c) || Character.isSpaceChar(c)`. It therefore
     * removes every Unicode space separator — including the non-breaking spaces
     * U+00A0, U+2007 and U+202F — together with U+0020, U+0009–U+000D, U+1680,
     * U+2000–U+200A, U+2028, U+2029, U+205F and U+3000. That matches §3's
     * "trim 前后 Unicode whitespace" literally and is the platform default, not
     * an Auralis-specific rule. Interior whitespace is never touched.
     */
    fun normalize(value: String?): String? {
        if (value == null) return null
        val trimmed = value.trim()
        // NFC can only recompose, never delete, so an empty trimmed value stays empty.
        return Normalizer.normalize(trimmed, Normalizer.Form.NFC).ifEmpty { null }
    }

    /**
     * Identity canonicalization for a multi-value field (Artist, Album Artist,
     * Genre): normalize every value, drop null/empty, exact-deduplicate, then
     * sort lexicographically by the normalized value.
     *
     * Identity therefore has set semantics: `["A", "B"]` and `["B", "A"]` are
     * identical material. Display must keep using the original ordered list —
     * the sort here must never feed back into UI artist credit order.
     *
     * The sort is Kotlin's natural `String` ordering (UTF-16 code unit order),
     * which is locale independent and therefore deterministic across devices.
     *
     * Phase 3 never splits values: the `List<String>` arriving from Phase 2C is
     * already separated on the English semicolon only.
     */
    fun canonicalCollection(values: List<String>): List<String> =
        values.mapNotNull { normalize(it) }.distinct().sorted()
}
