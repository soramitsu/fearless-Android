package jp.co.soramitsu.common.wallet

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.common.data.network.solana.RetrofitSolanaRpcClient
import jp.co.soramitsu.common.data.network.solana.SolanaRpcClient
import jp.co.soramitsu.common.data.network.solana.SolanaRpcException
import jp.co.soramitsu.common.data.network.solana.SolanaRpcApi
import jp.co.soramitsu.common.data.network.solana.SolanaRpcBalanceSource
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SolanaRpcClusterBindingTest {
    @Test fun `wrong cluster cannot be relabelled as mainnet balance`() = runBlocking {
        val api = ClusterApi { DEVNET }
        val result = runCatching { SolanaRpcBalanceSource(RetrofitSolanaRpcClient(api)).balances(
            OWNER, UniversalWalletRegistry.solanaMainnet, FIRST, false
        ) }
        assertEquals(SolanaRpcException.Code.GENESIS_MISMATCH, (result.exceptionOrNull() as? SolanaRpcException)?.code)
        assertEquals(listOf(FIRST to "getGenesisHash"), api.calls)
    }

    @Test fun `wrong primary is rejected before the verified fallback reads balance`() = runBlocking {
        val api = ClusterApi { if (it == FIRST) DEVNET else MAINNET }
        val client = RetrofitSolanaRpcClient(api).withEndpoints(listOf(FIRST, SECOND))
        assertEquals(42L, client.balance(OWNER))
        assertEquals(listOf(FIRST to "getGenesisHash", SECOND to "getGenesisHash", SECOND to "getBalance"), api.calls)
    }

    @Test fun `a previously valid endpoint cannot switch clusters before broadcast`() = runBlocking {
        var genesis = MAINNET
        val api = ClusterApi { genesis }
        val client = RetrofitSolanaRpcClient(api).withEndpoints(listOf(FIRST))
        assertEquals(42L, client.balance(OWNER))
        genesis = DEVNET
        val result = runCatching { client.sendRawTransaction("AQIDBA==") }
        assertTrue("Cluster must be rechecked before each request", result.isFailure)
        assertEquals(listOf(FIRST to "getGenesisHash", FIRST to "getBalance", FIRST to "getGenesisHash"), api.calls)
    }

    @Test fun `missing or malformed cluster identity cannot become zero balance`() = runBlocking {
        for (genesis in listOf("", "not-base58", "11111111111111111111111111111111")) {
            val api = ClusterApi { genesis }
            val result = runCatching { RetrofitSolanaRpcClient(api).withEndpoints(listOf(FIRST)).balance(OWNER) }
            assertTrue("Unverified endpoint must remain unavailable", result.isFailure)
            assertEquals(listOf(FIRST to "getGenesisHash"), api.calls)
        }
    }

    @Test fun `recovery history rent and simulation also require the selected cluster`() = runBlocking {
        val api = ClusterApi { DEVNET }
        val actions: List<suspend (SolanaRpcClient) -> Any?> = listOf(
            { it.signatureStatus(SIGNATURE) }, { it.blockHeight() }, { it.recentPrioritizationFees() },
            { it.minimumBalanceForRentExemption(165) }, { it.simulateTransaction("AQIDBA==") },
            { it.latestBlockhash() }, { it.feeForMessage("AQIDBA==") }, { it.accountInfo(OWNER) },
            { it.accountExists(OWNER) }, { it.tokenAccounts(OWNER, "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA") }
        )
        for (action in actions) {
            val client = RetrofitSolanaRpcClient(api).withEndpoints(listOf(FIRST))
            val failure = runCatching { action(client) }.exceptionOrNull()
            assertEquals(SolanaRpcException.Code.GENESIS_MISMATCH, (failure as? SolanaRpcException)?.code)
        }
        assertEquals(List(actions.size) { FIRST to "getGenesisHash" }, api.calls)
    }

    @Test fun `balance source selects devnet identity instead of its default mainnet client`() = runBlocking {
        val api = ClusterApi { DEVNET }
        val result = SolanaRpcBalanceSource(RetrofitSolanaRpcClient(api)).balances(
            OWNER, UniversalWalletRegistry.solanaDevnet, FIRST, false
        )
        assertEquals("solana:devnet", result.chainId)
        assertEquals("42", result.nativeBalance.amount)
        assertEquals(listOf("getGenesisHash", "getBalance", "getGenesisHash", "getTokenAccountsByOwner",
            "getGenesisHash", "getTokenAccountsByOwner"), api.calls.map { it.second })
        assertTrue(api.calls.all { it.first == FIRST })
    }

    @Test fun `endpoint changes preserve explicit cluster identity in either binding order`() = runBlocking {
        val api = ClusterApi { DEVNET }
        val client = RetrofitSolanaRpcClient(api)
        assertEquals(42L, client.withGenesisHash(DEVNET).withEndpoints(listOf(FIRST)).balance(OWNER))
        assertEquals(42L, client.withEndpoints(listOf(SECOND)).withGenesisHash(DEVNET).balance(OWNER))
        assertEquals(listOf(FIRST to "getGenesisHash", FIRST to "getBalance", SECOND to "getGenesisHash", SECOND to "getBalance"), api.calls)
    }

    @Test fun `URL alone cannot authorize a different cluster and invalid bindings never fetch`() = runBlocking {
        val api = ClusterApi { DEVNET }
        val client = RetrofitSolanaRpcClient(api)
        assertTrue(runCatching { client.withGenesisHash("not-base58") }.isFailure)
        assertTrue(api.calls.isEmpty())
        val failure = runCatching { client.balance(OWNER, rpcUrl = UniversalWalletRegistry.SOLANA_DEVNET_RPC_URL) }.exceptionOrNull()
        assertEquals(SolanaRpcException.Code.GENESIS_MISMATCH, (failure as? SolanaRpcException)?.code)
        assertEquals(listOf(UniversalWalletRegistry.SOLANA_DEVNET_RPC_URL to "getGenesisHash"), api.calls)
    }

    private class ClusterApi(private val genesis: (String) -> String) : SolanaRpcApi {
        val calls = mutableListOf<Pair<String, String>>()
        override suspend fun postJsonRpc(url: String, body: JsonObject): JsonObject {
            val method = body["method"].asString
            calls += url to method
            val result = when (method) {
                "getGenesisHash" -> "\"${genesis(url)}\""
                "getBalance" -> """{"context":{"slot":1},"value":42}"""
                "getTokenAccountsByOwner" -> """{"context":{"slot":1},"value":[]}"""
                "sendTransaction" -> "\"$SIGNATURE\""
                "getSignatureStatuses" -> """{"context":{"slot":1},"value":[null]}"""
                "getBlockHeight", "getMinimumBalanceForRentExemption" -> "1"
                "simulateTransaction" -> """{"context":{"slot":1},"value":{"err":null}}"""
                else -> error("Unexpected RPC: $method")
            }
            return JsonParser.parseString("""{"jsonrpc":"2.0","id":${body["id"]},"result":$result}""").asJsonObject
        }
    }

    private companion object {
        const val MAINNET = "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdpKuc147dw2N9d"
        const val DEVNET = "EtWTRABZaYq6iMfeYKouRu166VU2xqa1wcaWoxPkrZBG"
        const val FIRST = "https://first.example"
        const val SECOND = "https://second.example"
        const val OWNER = "11111111111111111111111111111111"
        const val SIGNATURE = "1111111111111111111111111111111111111111111111111111111111111111"
    }
}
