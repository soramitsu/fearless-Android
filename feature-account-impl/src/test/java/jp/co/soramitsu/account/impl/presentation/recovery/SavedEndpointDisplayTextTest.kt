package jp.co.soramitsu.account.impl.presentation.recovery

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedEndpointDisplayTextTest {
    @Test fun quotesEmptyAndRetainsSignificantSpacesUnicodeAndEndpointCredentials() {
        assertEquals("\"\"", savedEndpointDisplayText(""))
        assertEquals("\"  日本語 🐈  \"", savedEndpointDisplayText("  日本語 🐈  "))
        assertEquals("\"wss://user:password@example.invalid/path?key=exact#part\"",
            savedEndpointDisplayText("wss://user:password@example.invalid/path?key=exact#part"))
    }

    @Test fun escapesControlsBidiAndInvisibleSeparators() {
        assertEquals("\"a\\u0000\\u000a\\u000d\\u0009\\u007f\\u0085\\u202e\\u200b\\u2028\\u2029\\u00a0b\"",
            savedEndpointDisplayText("a\u0000\n\r\t\u007f\u0085\u202e\u200b\u2028\u2029\u00a0b"))
    }

    @Test fun literalBackslashesAndQuotesCannotMasqueradeAsEscapedCharacters() {
        assertEquals("\"\\\\u0000\\\"\\\\\"", savedEndpointDisplayText("\\u0000\"\\"))
    }

    @Test fun fullReaderBoundaryRemainsVisibleWithoutTruncation() {
        val value = "x".repeat(2047) + "\u0000"
        assertEquals("\"" + "x".repeat(2047) + "\\u0000\"", savedEndpointDisplayText(value))
    }

    @Test fun supplementaryFormattingTagsAreVisibleAndCannotMasqueradeAsLiteralEscapes() {
        assertEquals("\"a\\udb40\\udc01\\udb40\\udc20\\udb40\\udc7fb\"",
            savedEndpointDisplayText("a\udb40\udc01\udb40\udc20\udb40\udc7fb"))
        assertEquals("\"\\\\udb40\\\\udc01\"", savedEndpointDisplayText("\\udb40\\udc01"))
    }

    @Test fun printableSupplementaryCharactersRemainWholeAndUnpairedSurrogatesAreExplicit() {
        assertEquals("\"雪 🐈 𐐷\"", savedEndpointDisplayText("雪 🐈 𐐷"))
        assertEquals("\"\\ud800x\\udc00\"", savedEndpointDisplayText("\ud800x\udc00"))
    }
}
