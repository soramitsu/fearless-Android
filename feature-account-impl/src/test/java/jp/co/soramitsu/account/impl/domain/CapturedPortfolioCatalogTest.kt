package jp.co.soramitsu.account.impl.domain

import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.data.repository.CurrentAccountAssetContext
import jp.co.soramitsu.wallet.impl.data.repository.CurrentAccountAssetSnapshot
import jp.co.soramitsu.wallet.impl.domain.model.FiatValuationContext
import org.junit.Assert.*
import org.junit.Test

class CapturedPortfolioCatalogTest {
    @Test fun aggregationRetainsTheExistingReaderCatalogWithoutAnotherMapCopy() {
        val meta = mockk<MetaAccount>()
        val chain = mockk<Chain>(); val catalog = mapOf("a" to chain)
        val snapshot = CurrentAccountAssetSnapshot(CurrentAccountAssetContext(meta, FiatValuationContext("cad")),
            catalog, emptyList(), true)
        val result = calculateTrustedTotalBalance(snapshot, { false }, { _, _ -> true }) as TotalBalance.Ready
        assertSame(catalog, result.chainsById); assertSame(chain, result.chainsById["a"])
        assertSame(meta, result.meta); assertEquals("cad", result.currencyId)
    }

    @Test fun replacementCatalogCannotRewriteTheHistoricalReadyCatalog() {
        val before = mockk<Chain>(); val after = mockk<Chain>()
        val context = CurrentAccountAssetContext(mockk(), FiatValuationContext("cad"))
        val first = CurrentAccountAssetSnapshot(context, mapOf("a" to before), emptyList(), true)
        val replacement = CurrentAccountAssetSnapshot(context, mapOf("a" to after), emptyList(), false)
        val old = calculateTrustedTotalBalance(first, { false }, { _, _ -> true }) as TotalBalance.Ready
        val current = calculateTrustedTotalBalance(replacement, { false }, { _, _ -> true }) as TotalBalance.Ready
        assertNotSame(old, current); assertSame(before, old.chainsById["a"])
        assertSame(after, current.chainsById["a"]); assertSame(replacement.chainsById, current.chainsById)
        assertTrue(old.quotesReady); assertFalse(current.quotesReady)
    }
}
