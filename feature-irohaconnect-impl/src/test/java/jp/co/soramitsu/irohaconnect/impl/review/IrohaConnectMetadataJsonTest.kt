package jp.co.soramitsu.irohaconnect.impl.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class IrohaConnectMetadataJsonTest {

    private val json = IrohaConnectMetadataJson

    @Test
    fun shouldRoundTripCanonicalJson_whenTextIsAlreadyCanonical() {
        listOf("null", "true", "-7", "\"a\\\"b\\\\c\\n\\u0001\"", "[1,[2,{}]]", "{\"10\":1,\"9\":2,\"a\":{\"b\":[]}}").forEach { text ->
            assertEquals(text, json.canonical(json.parse(text)))
        }
    }

    @Test
    fun shouldPrintJavaScriptPropertyOrder_whenDisplayingParsedObjects() {
        val value = json.parse("{\"b\":1,\"10\":2,\"a\":3,\"9\":4}")
        assertEquals("{\"9\":4,\"10\":2,\"b\":1,\"a\":3}", json.display(value))
        assertEquals("{\"10\":2,\"9\":4,\"a\":3,\"b\":1}", json.canonical(value))
        assertEquals(listOf("1", "2", "x", "01"), json.jsPropertyOrder(listOf("x", "2", "01", "1")))
    }

    @Test
    fun shouldParseLikeJsonParse_whenTextIsLenientButValid() {
        assertEquals("{\"a\":2}", json.canonical(json.parse(" { \"a\" : 1 , \"a\" : 2 } ")))
        assertEquals("100", json.canonical(json.parse("1e2")))
        assertEquals("0", json.canonical(json.parse("-0")))
        assertEquals("\"/é\"", json.canonical(json.parse("\"\\/\\u00e9\"")))
    }

    @Test
    fun shouldRefuseInvalidOrUnsafeJson() {
        listOf("", "{", "[1,]", "01", "1.5", "9007199254740993", "1e400", "\"\u0001\"", "\"\\x\"", "nul", "{\"a\":1}x", "[".repeat(80) + "]".repeat(80)).forEach { text ->
            assertThrows(text, MetadataJsonException::class.java) { json.parse(text) }
        }
    }

    @Test
    fun shouldApplyTheCodecsKeyAndSizeRules_whenNormalizing() {
        assertTrue(json.isValidKey("memo"))
        listOf("", "a b", "a@b", "a#b", "a\$b", "e\u0301", "a\u3000").forEach { assertFalse(it, json.isValidKey(it)) }
        assertThrows(MetadataJsonException::class.java) {
            json.normalize(json.parse("{\"a b\":1}"), 1, IrohaConnectMetadataJson.State(1))
        }
        assertThrows(MetadataJsonException::class.java) {
            json.normalize(json.parse("\"\\ud800\""), 1, IrohaConnectMetadataJson.State(1))
        }
        val deep = "[".repeat(33) + "]".repeat(33)
        assertThrows(MetadataJsonException::class.java) {
            json.normalize(json.parse(deep), 1, IrohaConnectMetadataJson.State(1))
        }
        json.normalize(json.parse("{\"a\":[1,2,3]}"), 1, IrohaConnectMetadataJson.State(1))
        assertTrue(json.compareUtf8("a", "b") < 0)
        assertTrue(json.compareUtf8("é", "z") > 0)
        assertTrue(json.isRustUnicodeWhitespace("a\u2028"))
        assertFalse(json.isWellFormedUnicode("\udc00"))
        assertEquals(3, json.utf8Length("aé"))
        assertTrue(json.isNfc("é"))
        assertEquals("\"\\u001f\"", json.quote("\u001f"))
    }

    @Test
    fun shouldRecogniseOnlyCanonicalV1ContractAddresses() {
        val address = "irohac1qyqqqqqqqqqqqq95fes93ygegsv5enq9mqsz6x4lv4vp9gg4yxgjw"
        assertTrue(IrohaContractAddress.isCanonical(address))
        listOf(
            address.uppercase(),
            address.dropLast(1) + "x",
            address.replace("irohac1", "iroha1"),
            "irohac1",
            "",
            address + "q".repeat(40)
        ).forEach { assertFalse(it, IrohaContractAddress.isCanonical(it)) }
    }
}
