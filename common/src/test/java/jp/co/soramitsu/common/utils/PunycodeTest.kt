package jp.co.soramitsu.common.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PunycodeTest {
    @Test
    fun shouldEncodeInternationalLabelsUsingKnownWireRepresentations() {
        val labels = mapOf(
            "bücher" to "xn--bcher-kva",
            "mañana" to "xn--maana-pta",
            "日本語" to "xn--wgv71a119e",
            "☃" to "xn--n3h",
            "😀" to "xn--e28h",
            "例え.テスト" to "xn--r8jz45g.xn--zckzah"
        )
        labels.forEach { (unicode, ascii) ->
            assertEquals(unicode, ascii, Punycode.encode(unicode))
            assertEquals(ascii, unicode, Punycode.decode(ascii))
            assertEquals(unicode, Punycode.decodeSafe(ascii))
        }
    }

    @Test
    fun shouldPreserveAsciiLabelsAndEmptySeparators() {
        listOf("", "wallet.example", "A-B.123", ".", "..", "www..example.").forEach {
            assertEquals(it, Punycode.encode(it))
            assertEquals(it, Punycode.decode(it))
        }
        assertEquals("www.xn--bcher-kva.example.", Punycode.encode("www.bücher.example."))
        assertEquals("www.bücher.example.", Punycode.decode("www.xn--bcher-kva.example."))
    }

    @Test
    fun shouldAcceptCaseInsensitivePrefixesAndDigits() {
        assertEquals("bücher", Punycode.decode("XN--bcher-KVA"))
        assertEquals("abc", Punycode.decode("xn--abc-"))
    }

    @Test
    fun shouldRejectMalformedOrOverflowingLabelsWithoutChangingSafeInput() {
        listOf("xn--%", "xn--é-a", "xn--a_", "xn--z", "xn--999999999999999999999999999999").forEach {
            assertNull(it, Punycode.decode(it))
            assertEquals(it, Punycode.decodeSafe(it))
        }
    }

    @Test
    fun shouldRoundTripRepeatedAndMixedSupplementaryCodePoints() {
        listOf("你好你好", "a😀b☃c😀", "éàèêë.世界", "δοκιμή.пример").forEach {
            val encoded = requireNotNull(Punycode.encode(it))
            assertEquals(it, Punycode.decode(encoded))
        }
    }
}
