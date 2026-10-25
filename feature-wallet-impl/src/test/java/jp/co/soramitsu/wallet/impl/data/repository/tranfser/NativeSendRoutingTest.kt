package jp.co.soramitsu.wallet.impl.data.repository.tranfser

import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.wallet.impl.presentation.send.NativeSendExplorer
import org.junit.Assert.*
import org.junit.Test

class NativeSendRoutingTest {
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
        assertEquals(listOf(selected, other), NativeSendRpcEndpoints.resolve(chain.copy(nodes = nodes)))
    }

    @Test fun selectedFragmentRejectsAndInvalidInactiveEndpointDoesNotSuppressSelection() {
        val chain = BitcoinTransferServiceProviderTest.solanaChain()
        val selected = "https://selected.invalid/rpc?key=public-fixture"
        val inactive = ChainNode("https://other.invalid/#fragment", "Other", false, true)
        assertEquals(listOf(selected), NativeSendRpcEndpoints.resolve(chain.copy(nodes = listOf(
            inactive, ChainNode(selected, "Selected", true, false)))))
        assertThrows(IllegalArgumentException::class.java) { NativeSendRpcEndpoints.resolve(chain.copy(nodes = listOf(
            ChainNode(selected + "#fragment", "Selected", true, false)))) }
    }

    @Test fun `RPC resolver keeps explicit private providers isolated and public failover within cluster`() {
        val main = BitcoinTransferServiceProviderTest.solanaChain()
        assertEquals(listOf(UniversalWalletRegistry.SOLANA_MAINNET_RPC_URL, "https://solana-rpc.publicnode.com"), NativeSendRpcEndpoints.resolve(main))
        for (url in listOf("http://127.0.0.1:9999", "https://private.invalid", "https://rpc.test.invalid")) {
            assertEquals(listOf(url), NativeSendRpcEndpoints.resolve(main.copy(nodes = listOf(ChainNode(url, "Private", true, true)))))
        }
        val dev = BitcoinTransferServiceProviderTest.solanaChain(UniversalWalletRegistry.solanaDevnet)
        assertEquals(listOf(UniversalWalletRegistry.SOLANA_DEVNET_RPC_URL), NativeSendRpcEndpoints.resolve(dev))
        assertThrows(NativeSendException::class.java) { NativeSendRpcEndpoints.resolve(BitcoinTransferServiceProviderTest.bitcoinChain()) }
    }

    @Test fun `known explorer preserves exact network and rejects untrusted path input`() {
        val btc = BitcoinTransferServiceProviderTest.bitcoinChain()
        assertEquals("https://mempool.space/tx/" + "a".repeat(64), NativeSendExplorer.url(btc, "a".repeat(64), true))
        assertEquals("https://mempool.space/address/" + BitcoinTransferServiceProviderTest.MAINNET_ADDRESS,
            NativeSendExplorer.url(btc, BitcoinTransferServiceProviderTest.MAINNET_ADDRESS, false))
        assertThrows(IllegalArgumentException::class.java) { NativeSendExplorer.url(btc, "../attacker", true) }
        assertEquals(NativeSendExplorer.url(btc, "a".repeat(64), true), NativeSendExplorer.url(btc.copy(isTestNet = true), "a".repeat(64), true))
        val dev = BitcoinTransferServiceProviderTest.solanaChain(UniversalWalletRegistry.solanaDevnet)
        assertTrue(NativeSendExplorer.url(dev, BitcoinTransferServiceProviderTest.SOLANA_ADDRESS, false)!!.endsWith("?cluster=devnet"))
        assertTrue(NativeSendExplorer.url(dev, "1".repeat(64), true)!!.contains("/tx/"))
        assertThrows(IllegalArgumentException::class.java) { NativeSendExplorer.url(dev, "https://evil.invalid", true) }
    }
}
