@file:Suppress("PackageNaming") // Preserve the existing runtime multiNetwork package.

package jp.co.soramitsu.runtime.multiNetwork.connection

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.exc.StreamConstraintsException
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.http.HttpService
import java.math.BigInteger

/** Exercise web3j's production HTTP deserializer without contacting a real RPC node. */
class EvmJsonParsingLimitsTest {
    @Test
    fun ordinaryRpcBlockNumberStillParses() {
        val client = clientFor("""{"jsonrpc":"2.0","id":1,"result":"0x2a"}""")
        try {
            assertEquals(BigInteger.valueOf(42), client.ethBlockNumber().send().blockNumber)
        } finally {
            client.shutdown()
        }
    }

    @Test
    fun blockAndTransactionContainerStillParses() {
        val client = clientFor("""{"jsonrpc":"2.0","id":1,"result":{"number":"0x2a","transactions":[]}}""")
        try {
            val block = client.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, true).send().block
            assertEquals(BigInteger.valueOf(42), block.number)
            assertEquals(emptyList<Any>(), block.transactions)
        } finally {
            client.shutdown()
        }
    }

    @Test
    fun excessivelyNestedRpcErrorDataFailsWithBoundedParseException() {
        val nested = "[".repeat(2_000) + "0" + "]".repeat(2_000)
        val client = clientFor("""{"jsonrpc":"2.0","id":1,"error":{"code":-32000,"message":"bad reply","data":$nested}}""")
        try {
            val failure = assertThrows(JsonProcessingException::class.java) { client.ethBlockNumber().send() }
            // web3j's Error.data deserializer may wrap the parser's bounded failure.
            assertTrue(generateSequence(failure as Throwable) { it.cause }.any { it is StreamConstraintsException })
        } finally {
            client.shutdown()
        }
    }

    private fun clientFor(json: String): Web3j {
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(json.toResponseBody())
                .build()
        }.build()
        return Web3j.build(HttpService("https://rpc.invalid", transport))
    }
}
