package jp.co.soramitsu.wallet.impl.data.repository.tranfser

import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.wallet.impl.presentation.send.NativeSendExplorer
import org.junit.Assert.*
import org.junit.Test

class NativeSendRoutingTest {
    private companion object {
        val PUBLIC = listOf(UniversalWalletRegistry.SOLANA_MAINNET_RPC_URL, "https://solana-rpc.publicnode.com",
            "https://public.rpc.solanavibestation.com")
        const val PROVIDER = "https://provider.invalid/v1/public-fixture-key"
    }

    @Test fun missingOrAmbiguousSelectionNeverFallsBackToDefault() {
        val chain = BitcoinTransferServiceProviderTest.solanaChain()
        for (nodes in listOf(emptyList(), chain.nodes.map { it.copy(isActive = false) }, chain.nodes + chain.nodes)) {
            assertThrows(IllegalArgumentException::class.java) { NativeSendRpcEndpoints.resolve(chain.copy(nodes = nodes)) }
        }
    }

    @Test fun selectedEndpointKeepsExactBytesAndPrecedesValidFallbacks() {
        val chain = BitcoinTransferServiceProviderTest.solanaChain()
        val selected = "https://selected.invalid/rpc//?key=public-fixture&x=%2F"
        val other = "https://other.invalid/path/"
        val nodes = listOf(ChainNode(other, "Other", false, true), ChainNode(selected, "Selected", true, false))
        assertEquals(listOf(selected) + PUBLIC + other, NativeSendRpcEndpoints.resolve(chain.copy(nodes = nodes), buildProvider = ""))
    }

    @Test fun selectedFragmentRejectsAndInvalidInactiveEndpointDoesNotSuppressSelection() {
        val chain = BitcoinTransferServiceProviderTest.solanaChain()
        val selected = "https://selected.invalid/rpc?key=public-fixture"
        val inactive = ChainNode("https://other.invalid/#fragment", "Other", false, true)
        assertEquals(listOf(selected) + PUBLIC, NativeSendRpcEndpoints.resolve(chain.copy(nodes = listOf(
            inactive, ChainNode(selected, "Selected", true, false))), buildProvider = ""))
        assertThrows(IllegalArgumentException::class.java) { NativeSendRpcEndpoints.resolve(chain.copy(nodes = listOf(
            ChainNode(selected + "#fragment", "Selected", true, false)))) }
    }

    @Test fun `RPC resolver keeps explicit private providers isolated and public failover within cluster`() {
        val main = BitcoinTransferServiceProviderTest.solanaChain()
        assertEquals(PUBLIC, NativeSendRpcEndpoints.resolve(main, buildProvider = ""))
        assertEquals(listOf(PROVIDER) + PUBLIC, NativeSendRpcEndpoints.resolve(main, buildProvider = PROVIDER))
        // A user-selected node is asked first; every request stays bound to the mainnet genesis hash.
        for (url in listOf("http://127.0.0.1:9999", "https://private.invalid", "https://rpc.test.invalid")) {
            assertEquals(listOf(url) + PUBLIC, NativeSendRpcEndpoints.resolve(main.copy(nodes = listOf(ChainNode(url, "Private", true, true))), buildProvider = ""))
        }
        val dev = BitcoinTransferServiceProviderTest.solanaChain(UniversalWalletRegistry.solanaDevnet)
        assertEquals(listOf(UniversalWalletRegistry.SOLANA_DEVNET_RPC_URL), NativeSendRpcEndpoints.resolve(dev, buildProvider = PROVIDER))
        assertThrows(NativeSendException::class.java) { NativeSendRpcEndpoints.resolve(BitcoinTransferServiceProviderTest.bitcoinChain()) }
    }

    @Test fun `known explorer preserves exact network and rejects untrusted path input`() {
        val btc = BitcoinTransferServiceProviderTest.bitcoinChain()
        assertEquals("https://mempool.space/tx/" + "a".repeat(64), NativeSendExplorer.url(btc, "a".repeat(64), true))
        assertEquals("https://mempool.space/address/" + BitcoinTransferServiceProviderTest.MAINNET_ADDRESS,
            NativeSendExplorer.url(btc, BitcoinTransferServiceProviderTest.MAINNET_ADDRESS, false))
        assertThrows(IllegalArgumentException::class.java) { NativeSendExplorer.url(btc, "../attacker", true) }
        assertEquals(NativeSendExplorer.url(btc, "a".repeat(64), true), NativeSendExplorer.url(btc.copy(isTestNet = true), "a".repeat(64), true))
        val main = BitcoinTransferServiceProviderTest.solanaChain()
        // Solana history and transactions open on Solscan, as in the browser extension.
        assertEquals("https://solscan.io/account/" + BitcoinTransferServiceProviderTest.SOLANA_ADDRESS,
            NativeSendExplorer.url(main, BitcoinTransferServiceProviderTest.SOLANA_ADDRESS, false))
        assertEquals("https://solscan.io/tx/" + "1".repeat(64), NativeSendExplorer.url(main, "1".repeat(64), true))
        val dev = BitcoinTransferServiceProviderTest.solanaChain(UniversalWalletRegistry.solanaDevnet)
        assertEquals("https://solscan.io/account/" + BitcoinTransferServiceProviderTest.SOLANA_ADDRESS + "?cluster=devnet",
            NativeSendExplorer.url(dev, BitcoinTransferServiceProviderTest.SOLANA_ADDRESS, false))
        assertTrue(NativeSendExplorer.url(dev, "1".repeat(64), true)!!.contains("/tx/"))
        assertThrows(IllegalArgumentException::class.java) { NativeSendExplorer.url(dev, "https://evil.invalid", true) }
    }
}
