package jp.co.soramitsu.account.impl.domain

import jp.co.soramitsu.account.api.domain.model.AssetBalance
import jp.co.soramitsu.account.api.presentation.account.PortfolioValuationStatus
import jp.co.soramitsu.account.api.presentation.account.presentAssetBalance
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatFiat
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class AssetBalancePresentationTest {
    private fun ready(source: jp.co.soramitsu.wallet.impl.data.repository.CurrentAccountAssetSnapshot = AssetBalanceFixtures.snapshot()) =
        calculateAssetBalance(source, "chain", "asset", { false }, { _, _ -> true }) as AssetBalance.Ready

    @Test fun initialLoadingAndFailureNeverFormatZeroOrRetainMoney() {
        for (source in listOf(AssetBalance.AwaitingSelection, AssetBalance.Loading(7, "cad", "chain", "asset"), AssetBalance.Unavailable)) {
            val value = presentAssetBalance(source)
            assertSame(source, value.source); assertNull(value.transferable); assertNull(value.fiatValue); assertNull(value.rateChange)
            assertEquals(if (source == AssetBalance.Unavailable) PortfolioValuationStatus.Unavailable else PortfolioValuationStatus.Loading, value.status)
        }
    }
    @Test fun completedStateFormatsOnlyItsCapturedAssetAndCurrency() {
        val source = ready(); val value = presentAssetBalance(source)
        assertSame(source, value.source); assertEquals(BigDecimal("2").formatCrypto("UNIT"), value.transferable)
        assertEquals(BigDecimal("4").formatFiat("cad"), value.fiatValue); assertEquals(PortfolioValuationStatus.Complete, value.status)
    }
    @Test fun unknownCryptoDoesNotExposeKnownCryptoSubtotalAsComplete() {
        val source = ready(AssetBalanceFixtures.snapshot(rows = listOf(AssetBalanceFixtures.row(), AssetBalanceFixtures.row(free = null))))
        val value = presentAssetBalance(source)
        assertNull(value.transferable); assertEquals(BigDecimal("4").formatFiat("cad"), value.fiatValue)
        assertEquals(PortfolioValuationStatus.Partial, value.status); assertNull(value.rateChange)
    }
    @Test fun pendingQuotesKeepKnownCryptoAndPendingFiatStatus() {
        val source = ready(AssetBalanceFixtures.snapshot(rows = listOf(AssetBalanceFixtures.row().copy(token = null)), ready = false))
        val value = presentAssetBalance(source)
        assertNotNull(value.transferable); assertNull(value.fiatValue); assertEquals(PortfolioValuationStatus.AwaitingPrices, value.status)
    }
    @Test fun completeZeroIsFormattedWhileUnpricedBalanceIsUnavailable() {
        val zero = presentAssetBalance(ready(AssetBalanceFixtures.snapshot(rows = listOf(AssetBalanceFixtures.row(free = "2", rate = null)))))
        val unpriced = presentAssetBalance(ready(AssetBalanceFixtures.snapshot(rows = listOf(AssetBalanceFixtures.row(rate = null)))))
        assertNotNull(zero.transferable); assertNotNull(zero.fiatValue); assertEquals(PortfolioValuationStatus.Complete, zero.status)
        assertNotNull(unpriced.transferable); assertNull(unpriced.fiatValue); assertEquals(PortfolioValuationStatus.Unavailable, unpriced.status)
    }
    @Test fun missingSpecificAssetRowCannotRenderCompleteZero() {
        val source = calculateAssetBalance(AssetBalanceFixtures.snapshot(rows = emptyList()), "chain", "asset", { false }, { _, _ -> true })
        val value = presentAssetBalance(source)
        assertSame(AssetBalance.Unavailable, value.source)
        assertNull(value.transferable); assertNull(value.fiatValue)
        assertEquals(PortfolioValuationStatus.Unavailable, value.status)
    }
    @Test fun unknownChangeDoesNotInventZeroPercentOrHideCurrentValue() {
        val value = presentAssetBalance(ready(AssetBalanceFixtures.snapshot(rows = listOf(AssetBalanceFixtures.row(change = null)))))
        assertNotNull(value.fiatValue); assertNull(value.rateChange); assertEquals(PortfolioValuationStatus.Complete, value.status)
    }
}
