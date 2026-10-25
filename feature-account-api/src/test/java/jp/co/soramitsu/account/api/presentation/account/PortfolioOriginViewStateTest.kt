package jp.co.soramitsu.account.api.presentation.account

import android.graphics.drawable.PictureDrawable
import io.mockk.mockk
import java.math.BigDecimal
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.utils.formatFiat
import org.junit.Assert.*
import org.junit.Test

class PortfolioOriginViewStateTest {
    @Test fun completeOriginUsesCapturedNameMoneyAndIconTogether() {
        val source = originReady(meta = originMeta("Captured wallet"))
        val image = mockk<PictureDrawable>()
        val result = presentPortfolioOrigin(CapturedPortfolioDetail(source, AddressModel("address", image, "ignored name"))) {
            assertSame(source, it.source); assertEquals("cad", it.currencyId); null
        }
        assertSame(source, result.source); assertEquals("Captured wallet", result.name)
        assertSame(image, result.icon); assertEquals(BigDecimal.TEN.formatFiat("$"), result.balanceText)
    }

    @Test fun awaitingStateClearsNameIconAndAmountEvenWithAnInvalidOldDetail() {
        val old = AddressModel("old", mockk(), "Old wallet")
        val result = presentPortfolioOrigin(CapturedPortfolioDetail(TotalBalance.AwaitingSelection, old)) { "Loading balance" }
        assertEquals("", result.name); assertNull(result.icon); assertEquals("— · Loading balance", result.balanceText)
    }

    @Test fun failedPortfolioShowsUnavailableWithoutOldWalletData() {
        val result = presentPortfolioOrigin(CapturedPortfolioDetail<AddressModel>(TotalBalance.Unavailable, null)) {
            assertEquals(PortfolioValuationStatus.Unavailable, it.status); "Balance unavailable"
        }
        assertEquals("", result.name); assertNull(result.icon); assertEquals("— · Balance unavailable", result.balanceText)
    }

    @Test fun partialKnownZeroRemainsLabelledAndDistinctFromCompleteZero() {
        val partial = originReady(amount = null, subtotal = "0")
        val result = presentPortfolioOrigin(CapturedPortfolioDetail<AddressModel>(partial, null)) {
            assertEquals(PortfolioValuationStatus.Partial, it.status); "Partial balance"
        }
        assertEquals(BigDecimal.ZERO.formatFiat("$") + " · Partial balance", result.balanceText)
    }

    @Test fun unknownAmountRemainsPlaceholderWhileWalletIdentityIsKnown() {
        val source = originReady(amount = null, subtotal = null)
        val result = presentPortfolioOrigin(CapturedPortfolioDetail<AddressModel>(source, null)) { "Balance unavailable" }
        assertEquals(source.meta.name, result.name); assertNull(result.icon)
        assertEquals("— · Balance unavailable", result.balanceText)
    }

    @Test fun sameSymbolCurrencySwitchRetainsTheExactNewSourceAndLabelInput() {
        val source = originReady(currency = "usd")
        val next = originReady(meta = source.meta, currency = "cad", amount = "20")
        val before = presentPortfolioOrigin(CapturedPortfolioDetail<AddressModel>(source, null)) { null }
        val after = presentPortfolioOrigin(CapturedPortfolioDetail<AddressModel>(next, null)) {
            assertSame(next, it.source); assertEquals("cad", it.currencyId); "New unit"
        }
        assertSame(next, after.source); assertNotSame(before.source, after.source)
        assertEquals(BigDecimal("20").formatFiat("$") + " · New unit", after.balanceText)
    }

    @Test fun pendingQuotesShowLoadingStatusBesideAnExplicitPartialSubtotal() {
        val source = originReady(amount = null, subtotal = "10", quotesReady = false)
        val result = presentPortfolioOrigin(CapturedPortfolioDetail<AddressModel>(source, null)) {
            assertEquals(PortfolioValuationStatus.AwaitingPrices, it.status); "Partial balance · Loading prices"
        }
        assertEquals(BigDecimal.TEN.formatFiat("$") + " · Partial balance · Loading prices", result.balanceText)
    }

    @Test fun completedZeroDoesNotBecomeAPlaceholderOrPartialBalance() {
        val source = originReady(amount = "0")
        val result = presentPortfolioOrigin(CapturedPortfolioDetail<AddressModel>(source, null)) {
            assertEquals(PortfolioValuationStatus.Complete, it.status); null
        }
        assertEquals(BigDecimal.ZERO.formatFiat("$"), result.balanceText)
    }
}
