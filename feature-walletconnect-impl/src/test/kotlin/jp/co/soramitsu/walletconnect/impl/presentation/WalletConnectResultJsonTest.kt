package jp.co.soramitsu.walletconnect.impl.presentation

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletConnectResultJsonTest {

    @Test
    fun `polkadot answers go out as json objects`() {
        val result = walletConnectResultJson("""{"id":7,"signature":"0x01"}""")

        assertTrue(result is JsonObject)
        assertEquals("""{"id":7,"signature":"0x01"}""", result.toString())
    }

    @Test
    fun `arrays go out as json arrays`() {
        assertTrue(walletConnectResultJson("[1,2]") is JsonArray)
    }

    @Test
    fun `signatures and hashes go out as json strings`() {
        assertEquals(JsonPrimitive("0xabc"), walletConnectResultJson("0xabc"))
        assertEquals(JsonPrimitive("{not json"), walletConnectResultJson("{not json"))
    }
}
