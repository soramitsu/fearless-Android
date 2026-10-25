package jp.co.soramitsu.runtime.multiNetwork.chain

import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeWalletChainsTest {
    @Test
    fun `native icons resolve locally for canonical and retained aliases`() {
        val ids = setOf("bitcoin-mainnet", "bitcoin:mainnet", "solana-mainnet", "solana:mainnet")
        nativeWalletChains(ids).forEach { chain ->
            val expected = "file:///android_asset/native-icons/${chain.assets.single().priceId}.svg"
            assertEquals(expected, chain.icon)
            assertEquals(expected, chain.assets.single().iconUrl)
            assertEquals(expected, chain.assets.single().chainIcon)
        }
    }

    @Test
    fun `mainnet definitions survive persistence with exact native denomination and HTTP endpoints`() {
        val chains = nativeWalletChains().map { mapChainLocalToChain(mapChainToChainLocal(it)) }
        assertEquals(listOf("bitcoin-mainnet", "solana-mainnet"), chains.map(Chain::id))
        val bitcoin = chains.first()
        val solana = chains.last()
        assertEquals(UniversalWalletRegistry.BITCOIN_MAINNET_INDEXER_BASE_URL, bitcoin.externalApi?.history?.url)
        assertTrue(bitcoin.nodes.isEmpty())
        assertEquals(Chain.ExternalApi.Section.Type.BITCOIN, bitcoin.externalApi?.history?.type)
        assertEquals(UniversalWalletRegistry.SOLANA_MAINNET_RPC_URL, solana.nodes.single().url)
        assertEquals(Chain.ExternalApi.Section.Type.SOLANA, solana.externalApi?.history?.type)
        assertEquals(listOf("BTC", "SOL"), chains.map { it.assets.single().id })
        assertEquals(listOf(8, 9), chains.map { it.assets.single().precision })
        assertEquals(listOf("bitcoin", "solana"), chains.map { it.assets.single().priceId })
        chains.forEach {
            assertFalse(it.isTestNet)
            assertTrue(it.assets.single().isUtility)
            assertEquals(true, it.assets.single().isNative)
            assertEquals(null, it.xcm)
        }
    }

    @Test
    fun `historical aliases retain their exact chain and asset identity without enabling other networks`() {
        val chains = nativeWalletChains(setOf("bitcoin:mainnet", "solana:mainnet", "taira-testnet", "bitcoin-testnet"))
        assertEquals(setOf("bitcoin:mainnet", "solana:mainnet"), chains.map { it.id }.toSet())
        chains.forEach { chain -> assertEquals(chain.id, chain.assets.single().chainId) }
    }

    @Test
    fun `both already stored ids remain independently reachable`() {
        val ids = setOf("bitcoin-mainnet", "bitcoin:mainnet", "solana-mainnet", "solana:mainnet")
        assertEquals(ids, nativeWalletChains(ids).map { it.id }.toSet())
    }
}
