package com.bobo.auralis.mobile.library.db

import com.bobo.auralis.mobile.library.identity.TrackKeyStrength
import com.bobo.auralis.mobile.library.model.ParseState
import com.bobo.auralis.mobile.library.model.PlayabilityState
import com.bobo.auralis.mobile.library.model.SourceAvailabilityState
import com.bobo.auralis.mobile.library.scan.AudioFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class StringListCodecTest {

    @Test
    fun `empty list round-trips to empty array`() {
        val encoded = StringListCodec.encode(emptyList())
        assertEquals("[]", encoded)
        assertEquals(emptyList<String>(), StringListCodec.decode(encoded))
    }

    @Test
    fun `single item list round-trips`() {
        val items = listOf("Charli xcx")
        val encoded = StringListCodec.encode(items)
        assertEquals("[\"Charli xcx\"]", encoded)
        assertEquals(items, StringListCodec.decode(encoded))
    }

    @Test
    fun `multiple items maintain order`() {
        val items = listOf("Artist A", "Artist B", "Artist C")
        val encoded = StringListCodec.encode(items)
        assertEquals("[\"Artist A\",\"Artist B\",\"Artist C\"]", encoded)
        assertEquals(items, StringListCodec.decode(encoded))
    }

    @Test
    fun `special characters and escapes round-trip safely`() {
        val items = listOf(
            "Quotes \"inside\"",
            "Backslash \\ path",
            "Newline \n carriage \r tab \t",
            "Backspace \b formfeed \u000c",
            "Unicode escape \u0001 control",
            "Emoji 🎵 and CJK 中文测试 日本語 한국어",
        )
        val encoded = StringListCodec.encode(items)
        val decoded = StringListCodec.decode(encoded)
        assertEquals(items, decoded)
    }

    @Test
    fun `standard json escapes like forward slash are decoded`() {
        val json = "[\"a\\/b\"]"
        assertEquals(listOf("a/b"), StringListCodec.decode(json))
    }

    @Test
    fun `surrogate pairs and unicode escapes decode correctly`() {
        val json = "[\"\\u4e2d\\u6587\"]"
        assertEquals(listOf("中文"), StringListCodec.decode(json))
    }

    @Test
    fun `whitespace in array and around elements is tolerated`() {
        val json = "  [  \"item1\"  ,   \"item2\"  ]  "
        assertEquals(listOf("item1", "item2"), StringListCodec.decode(json))
    }

    @Test
    fun `malformed json inputs throw IllegalArgumentException`() {
        val malformedCases = listOf(
            "",
            "   ",
            "not json",
            "[",
            "[\"unclosed",
            "[\"item\",]",
            "[\"item1\" \"item2\"]",
            "{\"not\": \"array\"}",
            "[123]",
            "[\"item\"] trailing",
            "[\"\\u123\"]", // truncated \u
            "[\"\\uZZZZ\"]", // invalid hex
            "[\"\\x\"]", // invalid escape
        )

        for (malformed in malformedCases) {
            try {
                StringListCodec.decode(malformed)
                fail("Expected IllegalArgumentException for: '$malformed'")
            } catch (e: IllegalArgumentException) {
                // Expected
                assertTrue(e.message?.contains("malformed string list JSON") == true)
            }
        }
    }
}

class AuralisConvertersTest {

    private val converters = AuralisConverters()

    @Test
    fun `uuid converter round-trips and handles null`() {
        val uuid = UUID.randomUUID()
        val str = converters.uuidToString(uuid)
        assertEquals(uuid.toString(), str)
        assertEquals(uuid, converters.stringToUuid(str))

        assertNull(converters.uuidToString(null))
        assertNull(converters.stringToUuid(null))
    }

    @Test
    fun `availability state converter round-trips and handles null`() {
        for (state in SourceAvailabilityState.entries) {
            val str = converters.availabilityStateToString(state)
            assertEquals(state.name, str)
            assertEquals(state, converters.stringToAvailabilityState(str))
        }
        assertNull(converters.availabilityStateToString(null))
        assertNull(converters.stringToAvailabilityState(null))
    }

    @Test
    fun `parse state converter round-trips and handles null`() {
        for (state in ParseState.entries) {
            val str = converters.parseStateToString(state)
            assertEquals(state.name, str)
            assertEquals(state, converters.stringToParseState(str))
        }
        assertNull(converters.parseStateToString(null))
        assertNull(converters.stringToParseState(null))
    }

    @Test
    fun `playability state converter round-trips and handles null`() {
        for (state in PlayabilityState.entries) {
            val str = converters.playabilityStateToString(state)
            assertEquals(state.name, str)
            assertEquals(state, converters.stringToPlayabilityState(str))
        }
        assertNull(converters.playabilityStateToString(null))
        assertNull(converters.stringToPlayabilityState(null))
    }

    @Test
    fun `track key strength converter round-trips and handles null`() {
        for (strength in TrackKeyStrength.entries) {
            val str = converters.trackKeyStrengthToString(strength)
            assertEquals(strength.name, str)
            assertEquals(strength, converters.stringToTrackKeyStrength(str))
        }
        assertNull(converters.trackKeyStrengthToString(null))
        assertNull(converters.stringToTrackKeyStrength(null))
    }

    @Test
    fun `audio format converter round-trips and handles null`() {
        for (format in AudioFormat.entries) {
            val str = converters.audioFormatToString(format)
            assertEquals(format.name, str)
            assertEquals(format, converters.stringToAudioFormat(str))
        }
        assertNull(converters.audioFormatToString(null))
        assertNull(converters.stringToAudioFormat(null))
    }

    @Test
    fun `string list converter delegates to codec and handles null`() {
        val list = listOf("Alpha", "Beta", "Gamma")
        val json = converters.stringListToJson(list)
        assertEquals(list, converters.jsonToStringList(json))

        assertNull(converters.stringListToJson(null))
        assertNull(converters.jsonToStringList(null))
    }
}
