package jp.co.soramitsu.common.wallet

import com.google.gson.Gson
import com.google.gson.JsonParseException
import jp.co.soramitsu.common.data.network.iroha.IrohaMcpJsonRpcResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class IrohaMcpJsonRpcResponseAdapterTest {
    private val gson = Gson()

    @Test
    fun `wire version cannot be supplied by a model default`() {
        assertThrows(JsonParseException::class.java) {
            gson.fromJson("""{"id":"1","result":{}}""", IrohaMcpJsonRpcResponse::class.java)
        }
    }

    @Test
    fun `wire id cannot coerce a number to the matching request string`() {
        assertThrows(JsonParseException::class.java) {
            gson.fromJson("""{"jsonrpc":"2.0","id":1,"result":{}}""", IrohaMcpJsonRpcResponse::class.java)
        }
    }

    @Test
    fun `wire envelope requires explicit string fields and one object payload`() {
        listOf(
            """{"jsonrpc":2.0,"id":"1","result":{}}""",
            """{"jsonrpc":"1.0","id":"1","result":{}}""",
            """{"jsonrpc":"2.0","result":{}}""",
            """{"jsonrpc":"2.0","id":null,"result":{}}""",
            """{"jsonrpc":"2.0","id":"1"}""",
            """{"jsonrpc":"2.0","id":"1","result":{},"error":null}""",
            """{"jsonrpc":"2.0","id":"1","result":null}""",
            """{"jsonrpc":"2.0","id":"1","result":[]}""",
            """{"jsonrpc":"2.0","id":"1","result":true}""",
            """{"jsonrpc":"2.0","id":"1","error":null}""",
            "[]", "null"
        ).forEach { wire ->
            assertThrows(JsonParseException::class.java) { gson.fromJson(wire, IrohaMcpJsonRpcResponse::class.java) }
        }
    }

    @Test
    fun `wire success preserves map shape and a stale string id for client rejection`() {
        val response = gson.fromJson(
            """{"jsonrpc":"2.0","id":"stale","result":{"body":{"items":[]}}}""",
            IrohaMcpJsonRpcResponse::class.java
        )
        assertEquals("stale", response.id)
        val body = (response.result as Map<*, *>)["body"] as Map<*, *>
        assertEquals(emptyList<Any>(), body["items"])
    }

    @Test
    fun `wire error remains available to the bound response consumer`() {
        val response = gson.fromJson(
            """{"jsonrpc":"2.0","id":"1","error":{"code":-32000,"message":"unavailable"}}""",
            IrohaMcpJsonRpcResponse::class.java
        )
        assertEquals("1", response.id)
        assertNotNull(response.error)
        assertEquals(-32000, response.error?.code)
        assertEquals("unavailable", response.error?.message)
    }
}
