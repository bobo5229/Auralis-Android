package com.bobo.auralis.mobile.library.db

/**
 * Deterministic encoding for the `List<String>` columns of a source metadata
 * snapshot.
 *
 * `docs/phase3a/03_ROOM_SCHEMA.md` §13 allows a source snapshot to store its
 * artist / album artist / genre lists in one column, because that layer is a
 * per-source cache rather than the graph users query. §52 additionally requires
 * the encoding to be a JSON array or an equivalent dedicated converter, and to be
 * deterministic.
 *
 * Values are arbitrary Unicode text, so the encoding has to survive quotes,
 * backslashes, control characters and astral characters. Only characters that
 * JSON requires to be escaped are escaped, which keeps ordinary tags — including
 * Chinese text — readable in the database.
 *
 * Pure Kotlin: no `org.json`, because that is an Android platform class and would
 * make this untestable in a JVM unit test.
 */
object StringListCodec {

    fun encode(values: List<String>): String = buildString {
        append('[')
        values.forEachIndexed { index, value ->
            if (index > 0) append(',')
            appendJsonString(value)
        }
        append(']')
    }

    /**
     * @throws IllegalArgumentException when [encoded] is not a JSON array of
     *   strings. Corrupt data is reported rather than silently turned into an
     *   empty list, because an empty list would quietly look like "no artists".
     */
    fun decode(encoded: String): List<String> {
        val parser = Parser(encoded)
        val values = parser.parseArray()
        return values
    }

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        for (character in value) {
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else ->
                    if (character < ' ') {
                        append("\\u")
                        append(HEX[(character.code shr 12) and 0xF])
                        append(HEX[(character.code shr 8) and 0xF])
                        append(HEX[(character.code shr 4) and 0xF])
                        append(HEX[character.code and 0xF])
                    } else {
                        append(character)
                    }
            }
        }
        append('"')
    }

    private const val HEX = "0123456789abcdef"

    private class Parser(private val text: String) {
        private var position = 0

        fun parseArray(): List<String> {
            skipWhitespace()
            expect('[')
            skipWhitespace()

            val values = mutableListOf<String>()
            if (peek() == ']') {
                position++
                finish()
                return values
            }

            while (true) {
                values += parseString()
                skipWhitespace()
                when (val separator = next()) {
                    ',' -> skipWhitespace()
                    ']' -> {
                        finish()
                        return values
                    }
                    else -> fail("expected ',' or ']' but found '$separator'")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val builder = StringBuilder()
            while (true) {
                val character = next()
                when {
                    character == '"' -> return builder.toString()
                    character == '\\' -> builder.append(parseEscape())
                    else -> builder.append(character)
                }
            }
        }

        private fun parseEscape(): Char {
            val escape = next()
            return when (escape) {
                '"' -> '"'
                '\\' -> '\\'
                '/' -> '/'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'b' -> '\b'
                'f' -> '\u000C'
                'u' -> parseUnicodeEscape()
                else -> fail("unsupported escape '\\$escape'")
            }
        }

        private fun parseUnicodeEscape(): Char {
            if (position + 4 > text.length) fail("truncated \\u escape")
            val hex = text.substring(position, position + 4)
            position += 4
            return hex.toIntOrNull(16)?.toChar() ?: fail("invalid \\u escape '$hex'")
        }

        private fun skipWhitespace() {
            while (position < text.length && text[position].isJsonWhitespace()) position++
        }

        private fun peek(): Char = if (position < text.length) text[position] else fail("unexpected end of input")

        private fun next(): Char = peek().also { position++ }

        private fun expect(expected: Char) {
            val actual = next()
            if (actual != expected) fail("expected '$expected' but found '$actual'")
        }

        private fun finish() {
            skipWhitespace()
            if (position != text.length) fail("trailing content after the array")
        }

        private fun fail(reason: String): Nothing =
            throw IllegalArgumentException("malformed string list JSON at $position: $reason")

        private fun Char.isJsonWhitespace() = this == ' ' || this == '\t' || this == '\n' || this == '\r'
    }
}
