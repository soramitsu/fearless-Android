package jp.co.soramitsu.common.wallet

import com.google.gson.JsonObject
import jp.co.soramitsu.common.data.network.solana.RetrofitSolanaRpcClient
import jp.co.soramitsu.common.data.network.solana.SolanaRpcApi
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class SolanaPreferredConfigurationTest {
    private val first = "https://first.invalid/path?public-fixture=one"
    private val second = "https://second.invalid/path?public-fixture=two"

    private class Peer : SolanaRpcApi {
        val reads = mutableListOf<String>()
        var fail: String? = null
        var held: String? = null
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        override suspend fun postJsonRpc(url: String, body: JsonObject): JsonObject {
            val genesis = body["method"].asString == "getGenesisHash"
            if (!genesis) {
                reads += url
                if (url == held) {
                    entered.complete(Unit)
                release.await()
                }
                if (url == fail) throw IOException("public fixture unavailable")
            }
            return JsonObject().apply {
                addProperty("jsonrpc", "2.0")
                add("id", body["id"])
                if (genesis) {
                    addProperty("result", UniversalWalletRegistry.SOLANA_MAINNET_GENESIS_HASH)
                } else {
                    addProperty("result", 123L)
                }
            }
        }
    }

    @Test fun newOrderedConfigurationStartsAtNewPreferredPeer() = runBlocking<Unit> {
        val peer = Peer()
        val original = RetrofitSolanaRpcClient(peer).withEndpoints(listOf(first, second))
        original.blockHeight()
        peer.reads.clear()
        original.withEndpoints(listOf(second, first)).blockHeight()
        assertEquals(listOf(second), peer.reads)
    }

    @Test fun unchangedConfigurationRetainsDocumentedStickyFallback() = runBlocking<Unit> {
        val peer = Peer()
        peer.fail = first
        val original = RetrofitSolanaRpcClient(peer).withEndpoints(listOf(first, second))
        original.blockHeight()
        peer.reads.clear()
        peer.fail = null
        original.withEndpoints(listOf(first, second)).blockHeight()
        assertEquals(listOf(second), peer.reads)
    }

    @Test fun changedConfigurationDoesNotInheritSelectedPeerCooldown() = runBlocking<Unit> {
        val peer = Peer()
        peer.fail = first
        val original = RetrofitSolanaRpcClient(peer).withEndpoints(listOf(first, second))
        original.blockHeight()
        peer.fail = null
        peer.reads.clear()
        original.withEndpoints(listOf(first)).blockHeight()
        assertEquals(listOf(first), peer.reads)
    }

    @Test fun heldOldSuccessCannotChangeNewConfigurationPreference() = runBlocking<Unit> {
        val peer = Peer()
        peer.held = first
        val original = RetrofitSolanaRpcClient(peer).withEndpoints(listOf(first, second))
        val old = async { original.blockHeight() }
        withTimeout(5_000) { peer.entered.await() }
        val selected = original.withEndpoints(listOf(second, first))
        selected.blockHeight()
        peer.release.complete(Unit)
        withTimeout(5_000) { old.await() }
        peer.reads.clear()
        selected.blockHeight()
        assertEquals(listOf(second), peer.reads)
    }

    @Test fun newPreferredStillFailsOverWithinItsOwnConfiguration() = runBlocking<Unit> {
        val peer = Peer()
        val original = RetrofitSolanaRpcClient(peer).withEndpoints(listOf(first, second))
        original.blockHeight()
        peer.fail = second
        peer.reads.clear()
        original.withEndpoints(listOf(second, first)).blockHeight()
        assertEquals(listOf(second, first), peer.reads)
    }
}
