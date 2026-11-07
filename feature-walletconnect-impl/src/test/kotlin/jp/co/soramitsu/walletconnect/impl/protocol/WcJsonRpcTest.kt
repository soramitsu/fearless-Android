package jp.co.soramitsu.walletconnect.impl.protocol

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WcJsonRpcTest {

    @Test
    fun `requests results and errors round trip`() {
        val messages = listOf(
            WcJsonRpc.Request(1_700_000_000_000_123, "wc_sessionPing", buildJsonObject { put("a", 1) }),
            WcJsonRpc.Result(5, JsonPrimitive(true)),
            WcJsonRpc.Error(6, 5000, "User rejected.")
        )
        messages.forEach { message -> assertEquals(message, WcJsonRpcCodec.decode(WcJsonRpcCodec.encode(message))) }
    }

    @Test
    fun `encoded messages carry the json rpc version`() {
        assertTrue(WcJsonRpcCodec.encode(WcJsonRpc.Result(1, JsonPrimitive(true))).contains("\"jsonrpc\":\"2.0\""))
    }

    @Test
    fun `anything that is not json rpc 2 is not decoded`() {
        listOf(
            "not json",
            "[]",
            """{"id":1,"jsonrpc":"1.0","result":true}""",
            """{"id":"1","jsonrpc":"2.0","result":true}""",
            """{"id":1.5,"jsonrpc":"2.0","result":true}""",
            """{"id":1,"jsonrpc":"2.0"}""",
            """{"id":1,"jsonrpc":"2.0","method":5}""",
            """{"id":1,"jsonrpc":"2.0","error":{"message":"x"}}"""
        ).forEach { assertNull(it, WcJsonRpcCodec.decode(it)) }
    }

    @Test
    fun `a request without params gets json null`() {
        val request = WcJsonRpcCodec.decode("""{"id":7,"jsonrpc":"2.0","method":"wc_pairingPing"}""") as WcJsonRpc.Request
        assertEquals("null", request.params.toString())
    }

    @Test
    fun `ids follow the millisecond scheme and strictly increase`() {
        val clock = object : WcClock {
            override fun nowMillis(): Long = 1_700_000_000_000
        }
        val ids = WcIdGenerator(clock)
        val first = ids.next()
        val second = ids.next()

        assertEquals(1_700_000_000_000_000, first)
        assertEquals(first + 1, second)
        assertTrue(second < (1L shl 53))
    }

    @Test
    fun `parseObject returns objects only`() {
        assertNull(WcJsonRpcCodec.parseObject("[1]"))
        assertEquals("{}", WcJsonRpcCodec.parseObject("{}").toString())
    }
}
