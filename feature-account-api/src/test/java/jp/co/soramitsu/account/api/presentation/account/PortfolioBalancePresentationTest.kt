package jp.co.soramitsu.account.api.presentation.account

import io.mockk.mockk
import java.math.BigDecimal
import jp.co.soramitsu.account.api.domain.model.PortfolioBalance
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatFiat
import org.junit.Assert.*
import org.junit.Test

class PortfolioBalancePresentationTest {
    private fun partition(amount: String? = "10", subtotal: String? = amount,
                          change: String? = "-1", percent: String? = "-10", count: Int = 1,
                          unpriced: Int = 0, unknown: Int = 0) = PortfolioBalance(
        amount?.toBigDecimal(), subtotal?.toBigDecimal(), change?.toBigDecimal(), percent?.toBigDecimal(),
        count, if (subtotal != null && count > 0) 1 else 0, unpriced, unknown
    )
    private fun ready(shown: PortfolioBalance = partition(), hidden: PortfolioBalance = partition("0", count = 0),
                      all: PortfolioBalance = shown, currency: String = "cad", symbol: String = "cad",
                      quotesReady: Boolean = true) = TotalBalance.Ready(mockk(), emptyMap(), currency, symbol, quotesReady, shown, hidden, all)

    @Test fun initialAndLoadingStatesHaveNoNumberOrChange() {
        for (state in listOf(TotalBalance.AwaitingSelection, TotalBalance.Loading(7, "cad"))) {
            val result = presentTotalBalance(state, false)
            assertSame(state, result.source); assertNull(result.value); assertNull(result.change)
            assertEquals(PortfolioValuationStatus.Loading, result.status)
            assertEquals(0, result.hiddenExcludedCount)
        }
    }

    @Test fun failedReadCannotLookLikeCompletedEmptyWallet() {
        val result = presentTotalBalance(TotalBalance.Unavailable, true)
        assertNull(result.value); assertNull(result.change)
        assertEquals(PortfolioValuationStatus.Unavailable, result.status)
        assertFalse(result.includesHidden)
    }

    @Test fun completedEmptyWalletCanShowZeroWithItsCapturedUnit() {
        val source = ready(shown = partition("0", change = "0", percent = "0", count = 0))
        val result = presentTotalBalance(source, false)
        assertEquals(BigDecimal.ZERO.formatFiat("cad"), result.value)
        assertEquals(PortfolioValuationStatus.Complete, result.status)
        assertEquals("cad", result.currencyId); assertSame(source, result.source)
    }

    @Test fun completeValueUsesOnlyCapturedUnitAndKeepsSignedChange() {
        val source = ready(currency = "eur", symbol = "EUR ")
        val result = presentTotalBalance(source, false)
        assertEquals(BigDecimal("10").formatFiat("EUR "), result.value)
        assertEquals(BigDecimal("-10").formatAsChange(), result.change?.percentChange)
        assertEquals(BigDecimal.ONE.formatFiat("EUR "), result.change?.fiatChange)
        assertEquals("eur", result.currencyId)
    }

    @Test fun sameSymbolCurrenciesKeepDifferentIdentityAndSourceReferences() {
        val usd = ready(currency = "usd", symbol = "$")
        val cad = ready(currency = "cad", symbol = "$")
        val before = presentTotalBalance(usd, false); val after = presentTotalBalance(cad, false)
        assertEquals(before.value, after.value)
        assertEquals("usd", before.currencyId); assertEquals("cad", after.currencyId)
        assertSame(usd, before.source); assertSame(cad, after.source); assertNotSame(before.source, after.source)
    }

    @Test fun pricedSubtotalIsExplicitlyPartialAndNeverGetsACompleteChange() {
        val source = ready(partition(null, "10", unpriced = 1))
        val result = presentTotalBalance(source, false)
        assertEquals(BigDecimal.TEN.formatFiat("cad"), result.value)
        assertEquals(PortfolioValuationStatus.Partial, result.status); assertNull(result.change)
    }

    @Test fun partialKnownZeroIsNotACompletedZeroWallet() {
        val source = ready(partition(null, "0", unknown = 1))
        val result = presentTotalBalance(source, false)
        assertEquals(BigDecimal.ZERO.formatFiat("cad"), result.value)
        assertEquals(PortfolioValuationStatus.Partial, result.status); assertNull(result.change)
    }

    @Test fun allUnpricedOrUnknownValuesStayAbsent() {
        for (summary in listOf(partition(null, null, unpriced = 1), partition(null, null, unknown = 1))) {
            val result = presentTotalBalance(ready(summary), false)
            assertNull(result.value); assertNull(result.change)
            assertEquals(PortfolioValuationStatus.Unavailable, result.status)
        }
    }

    @Test fun pendingQuotesAreDistinctFromCompletedUnavailableQuotes() {
        val pending = presentTotalBalance(ready(partition(null, null), quotesReady = false), false)
        val completed = presentTotalBalance(ready(partition(null, null)), false)
        assertEquals(PortfolioValuationStatus.AwaitingPrices, pending.status)
        assertEquals(PortfolioValuationStatus.Unavailable, completed.status)
        assertNull(pending.value); assertNull(completed.value)
    }

    @Test fun emptyCryptoDoesNotWaitForAnUnneededQuote() {
        val source = ready(partition("0", count = 0), quotesReady = false)
        assertEquals(PortfolioValuationStatus.Complete, presentTotalBalance(source, false).status)
    }

    @Test fun missingRecentChangeKeepsKnownValueWithoutSyntheticZeroChange() {
        for (summary in listOf(partition(change = null), partition(percent = null))) {
            val result = presentTotalBalance(ready(summary), false)
            assertEquals(BigDecimal.TEN.formatFiat("cad"), result.value)
            assertEquals(PortfolioValuationStatus.Complete, result.status); assertNull(result.change)
        }
    }

    @Test fun walletManagementIncludesHiddenFundsAndLabelsTheirScope() {
        val source = ready(hidden = partition("5"), all = partition("15"))
        val header = presentTotalBalance(source, false); val management = presentTotalBalance(source, true)
        assertEquals(BigDecimal.TEN.formatFiat("cad"), header.value)
        assertEquals(BigDecimal("15").formatFiat("cad"), management.value)
        assertEquals(1, header.hiddenExcludedCount); assertFalse(header.includesHidden)
        assertEquals(0, management.hiddenExcludedCount); assertTrue(management.includesHidden)
    }

    @Test fun unknownHiddenHoldingsDoNotMakeWalletManagementLookEmpty() {
        val source = ready(shown = partition("0", count = 0), hidden = partition(null, null, unknown = 1),
            all = partition(null, null, unknown = 1))
        val result = presentTotalBalance(source, true)
        assertTrue(result.includesHidden); assertNull(result.value)
        assertEquals(PortfolioValuationStatus.Unavailable, result.status)
    }

    @Test fun readyToLoadingClearsPriorAmountCaptionAndChange() {
        val previous = presentTotalBalance(ready(hidden = partition("5")), false)
        assertNotNull(previous.value); assertEquals(1, previous.hiddenExcludedCount)
        val revoked = presentTotalBalance(TotalBalance.Loading(7, "eur"), false)
        assertNull(revoked.value); assertNull(revoked.change); assertEquals(0, revoked.hiddenExcludedCount)
        assertEquals("eur", revoked.currencyId)
    }
}
