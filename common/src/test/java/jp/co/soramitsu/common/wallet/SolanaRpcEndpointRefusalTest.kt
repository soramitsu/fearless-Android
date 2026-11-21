package jp.co.soramitsu.common.wallet

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.common.data.network.WalletProviderPool
import jp.co.soramitsu.common.data.network.solana.RetrofitSolanaRpcClient
import jp.co.soramitsu.common.data.network.solana.SolanaRpcApi
import jp.co.soramitsu.common.data.network.solana.SolanaRpcEndpoints
import jp.co.soramitsu.common.data.network.solana.SolanaRpcException
import jp.co.soramitsu.common.data.network.solana.SolanaTransferTransactionBuilder
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/** Public endpoints that refuse a call (HTTP 4xx) or cannot answer it must hand the call to the next endpoint. */
class SolanaRpcEndpointRefusalTest {
    private var now = 0L
    private val posts = mutableListOf<Pair<String, String>>()

    private fun client(answer: (url: String, method: String, id: Long) -> JsonObject): RetrofitSolanaRpcClient {
        val api = object : SolanaRpcApi {
            override suspend fun postJsonRpc(url: String, body: JsonObject): JsonObject {
                val method = body["method"].asString
                val id = body["id"].asLong
                if (method == "getGenesisHash") return result(id, "\"${UniversalWalletRegistry.SOLANA_MAINNET_GENESIS_HASH}\"")
                posts += url to method
                return answer(url, method, id)
            }
        }
        // Spacing has its own test; here every endpoint answers at once.
        return RetrofitSolanaRpcClient(api, providers = WalletProviderPool { now },
            pacer = jp.co.soramitsu.common.data.network.solana.SolanaRequestPacer({ 0 }) { now })
    }

    @Test
    fun `a refused read is answered by the next endpoint for 400 401 403 and 404`() = runBlocking {
        for (status in listOf(400, 401, 403, 404)) {
            posts.clear(); now += 60_000
            val client = client { url, _, id -> if (url == MAINNET_BETA) throw refusal(status) else result(id, BALANCE) }
            assertEquals(2_500_000_000L, client.balance(WALLET))
            assertEquals(listOf(MAINNET_BETA to "getBalance", PUBLICNODE to "getBalance"), posts)
        }
    }

    @Test
    fun `publicnode refusing token accounts leaves the token read to mainnet beta and the endpoint that served stays first`() = runBlocking {
        var mainnetBetaUp = false
        val client = client { url, method, id ->
            when {
                url == MAINNET_BETA && !mainnetBetaUp -> throw java.io.IOException("unreachable")
                url != MAINNET_BETA && method == "getTokenAccountsByOwner" -> throw refusal(403, "Request blocked")
                method == "getTokenAccountsByOwner" -> result(id, """{"context":{"slot":1},"value":[]}""")
                else -> result(id, BALANCE)
            }
        }
        assertEquals(2_500_000_000L, client.balance(WALLET))
        // Every endpoint refuses or fails: the refusal is reported, never an empty token list.
        val refused = assertThrows(HttpException::class.java) {
            runBlocking { client.tokenAccounts(WALLET, SolanaTransferTransactionBuilder.SPL_TOKEN_PROGRAM_ADDRESS) }
        }
        assertEquals(403, refused.code())
        now += 60_000; mainnetBetaUp = true; posts.clear()
        assertEquals(emptyList<Any>(), client.tokenAccounts(WALLET, SolanaTransferTransactionBuilder.SPL_TOKEN_PROGRAM_ADDRESS))
        assertEquals(listOf(PUBLICNODE to "getTokenAccountsByOwner", MAINNET_BETA to "getTokenAccountsByOwner"), posts)
        posts.clear()
        assertEquals(2_500_000_000L, client.balance(WALLET))
        assertEquals(listOf(MAINNET_BETA to "getBalance"), posts)
    }

    @Test
    fun `a read moves on after a JSON-RPC server error or an unreadable answer`() = runBlocking {
        for (failure in listOf<(Long) -> JsonObject>(
            { id -> error(id, -32010, "excluded from account secondary indexes") },
            { id -> error(id, -32603, "internal error") },
            { _ -> throw com.google.gson.JsonSyntaxException("not json") },
            { id -> result(id + 1, BALANCE) }
        )) {
            posts.clear(); now += 60_000
            val client = client { url, _, id -> if (url == MAINNET_BETA) failure(id) else result(id, BALANCE) }
            assertEquals(2_500_000_000L, client.balance(WALLET))
            assertEquals(2, posts.size)
        }
    }

    @Test
    fun `a refused broadcast goes to the next endpoint with the same bytes but a node's verdict is final`() = runBlocking {
        val refusing = client { url, _, id ->
            if (url == MAINNET_BETA) throw refusal(403) else result(id, "\"$SIGNATURE\"")
        }
        assertEquals(SIGNATURE, refusing.sendRawTransaction(TRANSACTION))
        assertEquals(listOf(MAINNET_BETA to "sendTransaction", PUBLICNODE to "sendTransaction"), posts)
        posts.clear(); now += 60_000
        val verdict = client { _, _, id -> error(id, -32002, "Transaction simulation failed: insufficient funds") }
        val rejected = assertThrows(jp.co.soramitsu.common.data.network.solana.SolanaBroadcastException::class.java) {
            runBlocking { verdict.sendRawTransaction(TRANSACTION) }
        }
        assertEquals(jp.co.soramitsu.common.data.network.solana.SolanaBroadcastException.Outcome.REFUSED, rejected.outcome)
        assertEquals(-32002, (rejected.cause as SolanaRpcException).rpcError?.code)
        assertEquals(listOf(MAINNET_BETA to "sendTransaction"), posts)
        posts.clear(); now += 60_000
        val lagging = client { url, _, id -> if (url == MAINNET_BETA) error(id, -32005, "node is behind") else result(id, "\"$SIGNATURE\"") }
        assertEquals(SIGNATURE, lagging.sendRawTransaction(TRANSACTION))
        assertEquals(2, posts.size)
    }

    private fun refusal(status: Int, body: String = "") =
        HttpException(Response.error<JsonObject>(status, body.toResponseBody("text/plain".toMediaType())))

    private companion object {
        const val MAINNET_BETA = UniversalWalletRegistry.SOLANA_MAINNET_RPC_URL
        const val PUBLICNODE = SolanaRpcEndpoints.PUBLICNODE_MAINNET_RPC_URL
        const val WALLET = "HAgk14JpMQLgt6rVgv7cBQFJWFto5Dqxi472uT3DKpqk"
        const val BALANCE = """{"context":{"slot":1},"value":2500000000}"""
        const val TRANSACTION = "AQIDBA=="
        const val SIGNATURE = "5NfHnqDyzT9qyfxZDq2sSskAMGuFZ3VRqW4EQxghKqrKYdKq6cZNW1J34w7qE6nGx1eDQe5s2eKxB2ZtE1xU9qgN"

        fun result(id: Long, json: String): JsonObject =
            JsonParser.parseString("""{"jsonrpc":"2.0","id":$id,"result":$json}""").asJsonObject

        fun error(id: Long, code: Int, message: String): JsonObject =
            JsonParser.parseString("""{"jsonrpc":"2.0","id":$id,"error":{"code":$code,"message":"$message"}}""").asJsonObject
    }
}
