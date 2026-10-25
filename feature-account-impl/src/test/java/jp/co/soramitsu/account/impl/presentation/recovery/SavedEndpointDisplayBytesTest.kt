package jp.co.soramitsu.account.impl.presentation.recovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SavedEndpointDisplayBytesTest {
    @Test fun emptyAndValidUtf8PreserveTextAndAllExistingEscapingRules() {
        assertEquals("\"\"", savedEndpointDisplayBytes(byteArrayOf()))
        listOf("  雪 🐈  ", "wss://public.invalid/Exact?Case=Yes", "a\u0000\n\u202e\udb40\udc01b", "\\xff\"").forEach {
            assertEquals(savedEndpointDisplayText(it), savedEndpointDisplayBytes(it.toByteArray(Charsets.UTF_8)))
        }
    }

    @Test fun malformedAndOverlongUtf8RemainExactBytesWithoutReplacementCharacters() {
        assertEquals("\"A\\xff\\xc0\\x80B\"", savedEndpointDisplayBytes(byteArrayOf(65, -1, -64, -128, 66)))
        assertEquals("\"\\xed\\xa0\\x80\\xf0\\x80\\x80\\xaf\"",
            savedEndpointDisplayBytes(byteArrayOf(-19, -96, -128, -16, -128, -128, -81)))
    }

    @Test fun splitMultibyteEdgesAreExplicitWhileCompleteInteriorCharactersStayReadable() {
        val snow = "雪".toByteArray(Charsets.UTF_8)
        assertEquals("\"\\xe9\\x9b\"", savedEndpointDisplayBytes(snow.copyOfRange(0, 2)))
        assertEquals("\"\\xaa\"", savedEndpointDisplayBytes(snow.copyOfRange(2, 3)))
        assertEquals("\"\\xaa雪\\xe9\"", savedEndpointDisplayBytes(byteArrayOf(snow[2]) + snow + byteArrayOf(snow[0])))
    }

    @Test fun literalEscapeTextCannotMasqueradeAsUndecodableBytes() {
        assertEquals("\"\\\\xff\"", savedEndpointDisplayBytes("\\xff".toByteArray()))
        assertEquals("\"\\xff\"", savedEndpointDisplayBytes(byteArrayOf(-1)))
    }

    @Test fun fullSliceRendersEveryByteWithoutEllipsisAndDoesNotAlterInput() {
        val input = ByteArray(512) { -1 }
        val before = input.copyOf()
        val rendered = savedEndpointDisplayBytes(input)
        assertEquals("\"" + "\\xff".repeat(512) + "\"", rendered)
        assertFalse(rendered.contains('\ufffd'))
        assertEquals(before.toList(), input.toList())
    }

    @Test fun malformedBytesNeverSkipTheirValidOrMalformedNeighbors() {
        assertEquals("\"N\\xff\\u0000\"", savedEndpointDisplayBytes(byteArrayOf(78, -1, 0)))
        assertEquals("\"\\x9f\\x98\\x80tail\"", savedEndpointDisplayBytes(byteArrayOf(-97, -104, -128, 116, 97, 105, 108)))
        assertEquals("\"" + "b".repeat(511) + "\\xf0\"", savedEndpointDisplayBytes(ByteArray(511) { 98 } + byteArrayOf(-16)))
        assertEquals("\"😀\\xff雪\"", savedEndpointDisplayBytes("😀".toByteArray() + byteArrayOf(-1) + "雪".toByteArray()))
    }

    @Test fun strictUtf8BoundaryScalarsAndInvalidSequencesKeepTheirIdentity() {
        listOf(0x80, 0x7ff, 0x800, 0xd7ff, 0xe000, 0xffff, 0x10000, 0x10ffff).forEach { scalar ->
            val text = String(Character.toChars(scalar))
            assertEquals(savedEndpointDisplayText(text), savedEndpointDisplayBytes(text.toByteArray(Charsets.UTF_8)))
        }
        listOf("c080", "c1bf", "e080af", "eda080", "edbfbf", "f08080af", "f4908080", "f5808080", "f880808080",
            "e282", "f09f98", "f08041", "e08041", "e24182").forEach { hex ->
            val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val escaped = hex.chunked(2).joinToString("") { if (it == "41") "A" else "\\x$it" }
            assertEquals("\"$escaped\"", savedEndpointDisplayBytes(bytes))
        }
    }

    @Test fun everyIsolatedByteIsVisibleWithoutInputMutationOrReplacement() {
        (0..255).forEach { value ->
            val input = byteArrayOf(value.toByte())
            val expected = if (value < 128) savedEndpointDisplayText(value.toChar().toString())
                else "\"\\x${value.toString(16).padStart(2, '0')}\""
            assertEquals(expected, savedEndpointDisplayBytes(input))
            assertEquals(value.toByte(), input.single())
        }
    }
}
