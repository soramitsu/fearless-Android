package jp.co.soramitsu.common.wallet

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.common.data.network.WalletProviderPool
import jp.co.soramitsu.common.data.network.solana.RetrofitSolanaRpcClient
import jp.co.soramitsu.common.data.network.solana.SolanaRequestPacer
import jp.co.soramitsu.common.data.network.solana.SolanaRpcApi
import jp.co.soramitsu.common.data.network.solana.SolanaRpcBalanceSource
import jp.co.soramitsu.common.data.network.solana.SolanaRpcEndpoints
import jp.co.soramitsu.common.data.network.solana.SolanaTransferTransactionBuilder
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * publicnode refuses token-account reads, so with mainnet-beta unavailable they fall to solanavibestation, which
 * answers HTTP 429 to any request that arrives within 600 ms of the previous one.
 */
class SolanaRateLimitedEndpointTest {
    private class Network(private val clock: () -> Long) : SolanaRpcApi {
        val posts = mutableListOf<Triple<String, String, Long>>()
        var rateLimited = 0
        private var lastVibestation: Long? = null

        override suspend fun postJsonRpc(url: String, body: JsonObject): JsonObject {
            val method = body["method"].asString
            val id = body["id"].asLong
            val now = clock()
            // Any spelling of a node's URL reaches the same node and its one rate limit.
            val node = url.trim().substringBefore('?').trimEnd('/').trimEnd('.').lowercase()
            posts += Triple(node, method, now)
            when (node) {
                MAINNET_BETA -> throw IOException("unreachable")
                PUBLICNODE -> if (method == "getTokenAccountsByOwner") throw http(403, "Request blocked")
                VIBESTATION -> {
                    val previous = lastVibestation
                    lastVibestation = now
                    if (previous != null && now - previous < 600) {
                        rateLimited++
                        throw http(429, "Too Many Requests")
                    }
                }
                else -> error("Unexpected endpoint $url")
            }
            return when (method) {
                "getGenesisHash" -> result(id, "\"${UniversalWalletRegistry.SOLANA_MAINNET_GENESIS_HASH}\"")
                "getBalance" -> result(id, """{"context":{"slot":1},"value":2500000000}""")
                "getTokenAccountsByOwner" -> {
                    val program = body["params"].asJsonArray[1].asJsonObject["programId"].asString
                    val wallet = body["params"].asJsonArray[0].asString
                    result(id, """{"context":{"slot":1},"value":${if (program == SPL) tokenRows(wallet) else "[]"}}""")
                }
                else -> error("Unexpected method $method")
            }
        }

        fun vibestationTimes() = posts.filter { it.first == VIBESTATION }.map { it.third }
    }

    private fun TestScope.client(network: Network, pacer: SolanaRequestPacer, vibestation: String = VIBESTATION) = RetrofitSolanaRpcClient(
        network, MAINNET_BETA, listOf(PUBLICNODE, vibestation), WalletProviderPool { testScheduler.currentTime },
        pacer = pacer
    )

    private fun TestScope.pacer(spacing: (String) -> Long = SolanaRpcEndpoints::requestSpacingMillis) =
        SolanaRequestPacer(spacing) { testScheduler.currentTime }

    private suspend fun balances(client: RetrofitSolanaRpcClient, wallet: String = WALLET, vibestation: String = VIBESTATION) =
        SolanaRpcBalanceSource(client) { 1234 }.balances(wallet, UniversalWalletRegistry.solanaMainnet, null, false,
            listOf(PUBLICNODE, vibestation))

    @Test
    fun `spaced requests let the rate-limited node serve both token programs that publicnode refuses`() = runTest {
        val network = Network { testScheduler.currentTime }
        val result = balances(client(network, pacer()))
        assertEquals("2500000000", result.nativeBalance.amount)
        assertTrue(result.tokensAvailable)
        assertEquals(listOf(MINT), result.tokenBalances.map { it.assetId })
        assertEquals(0, network.rateLimited)
        // Both token programs were read from the rate-limited node, each request at least 700 ms after the last.
        val tokenReads = network.posts.filter { it.second == "getTokenAccountsByOwner" }.map { it.first }
        assertEquals(listOf(PUBLICNODE, VIBESTATION, VIBESTATION), tokenReads)
        val times = network.vibestationTimes()
        assertEquals(4, times.size)
        assertTrue(times.zipWithNext().all { (previous, next) -> next - previous >= 700 })
    }

    @Test
    fun `without spacing the burst is refused and the 429 back-off leaves tokens unread`() = runTest {
        val network = Network { testScheduler.currentTime }
        val result = balances(client(network, pacer { 0 }))
        // SOL still loads, but the inventory is unknown: the cooled-down node is not asked again in this read.
        assertEquals("2500000000", result.nativeBalance.amount)
        assertFalse(result.tokensAvailable)
        assertTrue(result.tokenBalances.isEmpty())
        assertEquals(1, network.rateLimited)
    }

    @Test
    fun `the rate-limited node never becomes the endpoint later calls try first`() = runTest {
        val network = Network { testScheduler.currentTime }
        val client = client(network, pacer())
        assertTrue(balances(client).tokensAvailable)
        delay(5_000)
        network.posts.clear()
        assertEquals(2_500_000_000L, client.balance(WALLET))
        assertEquals(listOf(PUBLICNODE to "getGenesisHash", PUBLICNODE to "getBalance"), network.posts.map { it.first to it.second })
    }

    @Test
    fun `concurrent reads from several wallets share one pace for the rate-limited node`() = runTest {
        val network = Network { testScheduler.currentTime }
        val pacer = pacer()
        val results = listOf(WALLET, OTHER_WALLET).map { wallet -> async { balances(client(network, pacer), wallet) } }.awaitAll()
        assertTrue(results.all { it.tokensAvailable })
        assertEquals(0, network.rateLimited)
        assertTrue(network.vibestationTimes().zipWithNext().all { (previous, next) -> next - previous >= 700 })
    }

    @Test
    fun `other spellings of the rate-limited node share its pace and never become preferred`() = runTest {
        val network = Network { testScheduler.currentTime }
        val pacer = pacer()
        val spellings = listOf("$VIBESTATION/", "https://Public.Rpc.SolanaVibeStation.com", "$VIBESTATION.")
        val clients = spellings.map { client(network, pacer, it) }
        val results = clients.zip(listOf(WALLET, OTHER_WALLET, THIRD_WALLET)).zip(spellings) { (client, wallet), spelling ->
            async { balances(client, wallet, spelling) }
        }.awaitAll()
        assertTrue(results.all { it.tokensAvailable })
        assertEquals(0, network.rateLimited)
        assertTrue(network.vibestationTimes().zipWithNext().all { (previous, next) -> next - previous >= 700 })
        delay(5_000)
        network.posts.clear()
        assertEquals(2_500_000_000L, clients.first().balance(WALLET))
        assertEquals(listOf(PUBLICNODE to "getGenesisHash", PUBLICNODE to "getBalance"), network.posts.map { it.first to it.second })
    }

    private companion object {
        const val MAINNET_BETA = UniversalWalletRegistry.SOLANA_MAINNET_RPC_URL
        const val PUBLICNODE = SolanaRpcEndpoints.PUBLICNODE_MAINNET_RPC_URL
        const val VIBESTATION = SolanaRpcEndpoints.SOLANAVIBESTATION_MAINNET_RPC_URL
        const val SPL = SolanaTransferTransactionBuilder.SPL_TOKEN_PROGRAM_ADDRESS
        const val WALLET = "HAgk14JpMQLgt6rVgv7cBQFJWFto5Dqxi472uT3DKpqk"
        const val OTHER_WALLET = "AKnL4NNf3DGWZJS6cPknBuEGnVsV4A4m5tgebLHaRSZ9"
        const val THIRD_WALLET = "9xQeWvG816bUx9EPfFny7R5AySPGaWWSLS9eSg9wAYjr"
        const val MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
        const val ACCOUNT = "9GmvNe93LCNZpC88BSA3emPuTrSq7oep74BjTk11jBQe"

        fun tokenRows(wallet: String) = """[{"pubkey":"$ACCOUNT","account":{"owner":"$SPL","executable":false,"lamports":2039280,
            "data":{"parsed":{"type":"account","info":{"owner":"$wallet","mint":"$MINT","state":"initialized",
            "tokenAmount":{"amount":"1250000","decimals":6}}}}}}]""".trimIndent()

        fun result(id: Long, json: String): JsonObject =
            JsonParser.parseString("""{"jsonrpc":"2.0","id":$id,"result":$json}""").asJsonObject

        fun http(status: Int, body: String) =
            HttpException(Response.error<JsonObject>(status, body.toResponseBody("text/plain".toMediaType())))
    }
}
