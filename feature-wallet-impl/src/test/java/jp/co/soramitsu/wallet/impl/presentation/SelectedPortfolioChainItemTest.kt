package jp.co.soramitsu.wallet.impl.presentation

import jp.co.soramitsu.runtime.multiNetwork.chain.model.tonMainnetChainId
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.ChainSelectScreenContract.State.ItemState.Impl
import jp.co.soramitsu.wallet.impl.presentation.balance.list.selectedPortfolioChainItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelectedPortfolioChainItemTest {
    private val polkadot = Impl("polkadot", null, "Polkadot")
    private val ton = Impl(tonMainnetChainId, null, "TON Mainnet")

    @Test
    fun `TON header uses its canonical identity even when another network comes first`() {
        assertEquals(ton, selectedPortfolioChainItem(tonMainnetChainId, listOf(polkadot, ton)))
    }

    @Test
    fun `missing selected network never borrows an unrelated network label`() {
        assertNull(selectedPortfolioChainItem(tonMainnetChainId, listOf(polkadot)))
        assertNull(selectedPortfolioChainItem(tonMainnetChainId, emptyList()))
    }

    @Test
    fun `all networks uses a single network label only for a single item catalog`() {
        assertEquals(ton, selectedPortfolioChainItem(null, listOf(ton)))
        assertNull(selectedPortfolioChainItem(null, listOf(polkadot, ton)))
        assertNull(selectedPortfolioChainItem(null, emptyList()))
    }

    @Test
    fun `ordinary explicit network selection retains its own label`() {
        assertEquals(polkadot, selectedPortfolioChainItem(polkadot.id, listOf(ton, polkadot)))
    }
}
