package jp.co.soramitsu.common.wallet

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.common.data.network.solana.RetrofitSolanaRpcClient
import jp.co.soramitsu.common.data.network.solana.SolanaRpcApi
import jp.co.soramitsu.common.data.network.solana.SolanaRpcBalanceSource
import jp.co.soramitsu.common.data.network.solana.SolanaRpcException
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SolanaExactTransportTest {
    @Test fun trailingPathSlashAndEscapedQueryReachActualApiUnchanged() = runBlocking<Unit> {
        val peer = Peer()
        for (url in listOf("https://public.invalid/path/", "https://public.invalid/a//", "https://public.invalid/path/?key=public%2Bfixture%2F&x=one+two")) {
            peer.calls.clear()
            RetrofitSolanaRpcClient(peer).withEndpoints(listOf(url)).blockHeight()
            assertEquals(listOf(url, url), peer.calls.map { it.first })
        }
    }

    @Test fun fragmentsAreRejectedBeforeAnyRpcCall() = runBlocking<Unit> {
        val peer = Peer()
        assertThrows(SolanaRpcException::class.java) {
            RetrofitSolanaRpcClient(peer).withEndpoints(listOf("https://public.invalid/path/#fragment"))
        }
        assertTrue(peer.calls.isEmpty())
    }

    @Test fun whitespaceUserinfoRemoteCleartextAndInvalidPortsCannotBecomeTransport() {
        val peer = Peer()
        for (url in listOf(" https://public.invalid/path/", "https://public.invalid/path/ ", "https://user@public.invalid/", "http://public.invalid/", "https://public.invalid:0/", "https://public.invalid:65536/")) {
            assertThrows(url, SolanaRpcException::class.java) { RetrofitSolanaRpcClient(peer).withEndpoints(listOf(url)) }
        }
        assertTrue(peer.calls.isEmpty())
    }

    @Test fun balanceSourceConstructsSelectedPeerConfigurationAfterOldPeerSuccess() = runBlocking<Unit> {
        val peer = Peer()
        val root = RetrofitSolanaRpcClient(peer)
        root.blockHeight()
        peer.calls.clear()
        val selected = "https://solana-rpc.publicnode.com"
        val balance = SolanaRpcBalanceSource(root) { 1234 }.balances(
            "11111111111111111111111111111111", UniversalWalletRegistry.solanaMainnet, selected, false
        )
        assertEquals("42", balance.nativeBalance.amount)
        assertEquals(6, peer.calls.size)
        assertTrue(peer.calls.all { it.first == selected })
    }

    private class Peer : SolanaRpcApi {
        val calls = mutableListOf<Pair<String, String>>()
        override suspend fun postJsonRpc(url: String, body: JsonObject): JsonObject {
            val method = body["method"].asString
            calls += url to method
            val result = when (method) {
                "getGenesisHash" -> "\"${UniversalWalletRegistry.SOLANA_MAINNET_GENESIS_HASH}\""
                "getBalance" -> "{\"context\":{\"slot\":1},\"value\":42}"
                "getTokenAccountsByOwner" -> "{\"context\":{\"slot\":1},\"value\":[]}"
                "getBlockHeight" -> "123"
                else -> error("Unexpected public fixture method")
            }
            return JsonObject().apply {
                addProperty("jsonrpc", "2.0")
                add("id", body["id"])
                add("result", JsonParser.parseString(result))
            }
        }
    }
}
