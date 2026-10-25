package jp.co.soramitsu.runtime.ext

import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BitcoinTestnetAccountChainTest {
    @Test
    fun `missing catalog gets address-only testnet without Nexus or network endpoints`() {
        val row = emptyList<Chain>().withBitcoinTestnetAccountChain(emptySet()).single()
        assertEquals(UniversalWalletRegistry.bitcoinTestnet.id, row.id)
        assertTrue(row.isTestNet)
        assertTrue(row.nodes.isEmpty())
        assertTrue(row.assets.isEmpty())
        assertTrue(row.explorers.isEmpty())
        assertNull(row.externalApi)
        assertFalse(UniversalWalletRegistry.nexus.enabledByDefault)
        assertTrue(UniversalWalletRegistry.taira.enabledByDefault)
    }

    @Test
    fun `retains both stored aliases so discovery cannot cascade delete independent keys`() {
        val aliases = setOf(UniversalWalletRegistry.bitcoinTestnet.id, UniversalWalletRegistry.bitcoinTestnet.chainId)
        assertEquals(aliases, emptyList<Chain>().withBitcoinTestnetAccountChain(aliases).map { it.id }.toSet())
    }

    @Test
    fun `case variants retain their exact stored parent ids and cannot create duplicate canonical rows`() {
        val aliases = setOf("BITCOIN:TESTNET", "BitCoin-TestNet")
        val retained = emptyList<Chain>().withBitcoinTestnetAccountChain(aliases)
        assertEquals(aliases, retained.map { it.id }.toSet())
        assertSame(retained, retained.withBitcoinTestnetAccountChain(aliases))
        assertSame(retained, retained.withBitcoinTestnetAccountChain(emptySet()))
    }

    @Test
    fun `real catalog entry takes precedence without changing metadata`() {
        val rows = emptyList<Chain>().withBitcoinTestnetAccountChain(emptySet())
        assertSame(rows, rows.withBitcoinTestnetAccountChain(emptySet()))
        val alias = UniversalWalletRegistry.bitcoinTestnet.chainId
        val retained = rows.withBitcoinTestnetAccountChain(setOf(alias))
        assertSame(rows.single(), retained.first())
        assertEquals(alias, retained.last().id)
    }
}
