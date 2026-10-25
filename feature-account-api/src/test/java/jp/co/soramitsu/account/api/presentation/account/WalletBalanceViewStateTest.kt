package jp.co.soramitsu.account.api.presentation.account

import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.PortfolioBalance
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatFiat
import org.junit.Assert.*
import org.junit.Test

class WalletBalanceViewStateTest {
    private val resources = mockk<ResourceManager> {
        every { getString(R.string.portfolio_balance_loading) } returns "Loading balance"
        every { getString(R.string.portfolio_prices_loading) } returns "Loading prices"
        every { getString(R.string.portfolio_balance_partial) } returns "Partial balance"
        every { getString(R.string.portfolio_balance_unavailable) } returns "Balance unavailable"
        every { getString(R.string.portfolio_balance_incl_hidden) } returns "incl. hidden"
        every { getQuantityString(R.plurals.portfolio_hidden_assets_not_included, 1, 1) } returns "1 hidden asset not included"
    }

    private fun partition(amount: String?, subtotal: String? = amount, count: Int = 1) = PortfolioBalance(
        amount?.toBigDecimal(), subtotal?.toBigDecimal(), null, null,
        count, if (subtotal != null && count > 0) 1 else 0, if (amount == null) 1 else 0, 0
    )

    private fun ready(id: Long = 7, shown: PortfolioBalance = partition("10"),
                      hidden: PortfolioBalance = partition("0", count = 0),
                      all: PortfolioBalance = shown, quotesReady: Boolean = true): TotalBalance.Ready {
        val meta = mockk<MetaAccount> { every { this@mockk.id } returns id }
        return TotalBalance.Ready(meta, emptyMap(), "cad", "CAD ", quotesReady, shown, hidden, all)
    }

    private fun card() = WalletItemViewState(
        id = 7, title = "Wallet seven", walletIcon = "old-icon", isSelected = true,
        balance = "old amount", assetSymbol = "old unit", balanceCaption = "old caption",
        changeBalanceViewState = ChangeBalanceViewState("+100%", "old change")
    )

    @Test fun managementUsesAllCapturedHoldingsAndClearsLegacyUnitPrefix() {
        val source = ready(hidden = partition("5"), all = partition("15"))
        val result = card().withAllHoldings(source, resources)
        assertEquals(BigDecimal("15").formatFiat("CAD "), result.balance)
        assertEquals("incl. hidden", result.balanceCaption)
        assertNull(result.assetSymbol); assertNull(result.changeBalanceViewState)
        assertEquals("Wallet seven", result.title); assertEquals(7L, result.id)
    }

    @Test fun portfolioUsesShownHoldingsAndLabelsHiddenCount() {
        val source = ready(hidden = partition("5"), all = partition("15"))
        val result = card().withPortfolioBalance(source, resources)
        assertEquals(BigDecimal.TEN.formatFiat("CAD "), result.balance)
        assertEquals("1 hidden asset not included", result.balanceCaption)
        assertNull(result.assetSymbol)
    }

    @Test fun anotherWalletReadyCannotBeFormattedOnThisCard() {
        for (result in listOf(card().withAllHoldings(ready(id = 8), resources),
                              card().withPortfolioBalance(ready(id = 8), resources))) {
            assertNull(result.balance); assertNull(result.changeBalanceViewState)
            assertEquals("Balance unavailable", result.balanceCaption)
        }
    }

    @Test fun loadingOrFailureClearsEveryPreviousMonetaryField() {
        for (source in listOf(TotalBalance.Loading(7, "eur"), TotalBalance.AwaitingSelection, TotalBalance.Unavailable)) {
            val result = card().withPortfolioBalance(source, resources)
            assertNull(result.balance); assertNull(result.changeBalanceViewState); assertNull(result.assetSymbol)
            assertEquals(if (source == TotalBalance.Unavailable) "Balance unavailable" else "Loading balance", result.balanceCaption)
        }
    }

    @Test fun loadingForAnotherWalletIsUnavailableInsteadOfCurrentLoading() {
        val result = card().withAllHoldings(TotalBalance.Loading(8, "cad"), resources)
        assertNull(result.balance); assertEquals("Balance unavailable", result.balanceCaption)
    }

    @Test fun partialKnownZeroKeepsExplicitPartialCaption() {
        val result = card().withPortfolioBalance(ready(shown = partition(null, "0")), resources)
        assertEquals(BigDecimal.ZERO.formatFiat("CAD "), result.balance)
        assertEquals("Partial balance", result.balanceCaption); assertNull(result.changeBalanceViewState)
    }

    @Test fun pendingSubtotalShowsBothPartialAndPriceLoadingStatus() {
        val result = card().withPortfolioBalance(ready(shown = partition(null, "3"), quotesReady = false), resources)
        assertEquals(BigDecimal("3").formatFiat("CAD "), result.balance)
        assertEquals("Partial balance · Loading prices", result.balanceCaption)
    }

    @Test fun noValueHasVisibleUnavailableOrLoadingCaption() {
        val unavailable = card().withAllHoldings(ready(shown = partition(null, null)), resources)
        val pending = card().withAllHoldings(ready(shown = partition(null, null), quotesReady = false), resources)
        assertNull(unavailable.balance); assertEquals("Balance unavailable", unavailable.balanceCaption)
        assertNull(pending.balance); assertEquals("Loading prices", pending.balanceCaption)
    }

    @Test fun completeEmptyZeroDoesNotCarryStaleCaptionOrChange() {
        val result = card().withPortfolioBalance(ready(shown = partition("0", count = 0)), resources)
        assertEquals(BigDecimal.ZERO.formatFiat("CAD "), result.balance)
        assertNull(result.balanceCaption); assertNull(result.changeBalanceViewState)
    }

    @Test fun unknownHiddenHoldingsKeepScopeBesideUnavailableStatus() {
        val result = card().withAllHoldings(ready(hidden = partition(null, null), all = partition(null, null)), resources)
        assertNull(result.balance); assertEquals("Balance unavailable · incl. hidden", result.balanceCaption)
    }

    @Test fun directCaptionNeverDropsPartialStatusWhenHiddenCountExists() {
        val presentation = presentTotalBalance(ready(shown = partition(null, "10"), hidden = partition("5")), false)
        assertEquals("Partial balance · 1 hidden asset not included", portfolioBalanceCaption(presentation, resources))
    }

    @Test fun hiddenCaptionRequiresReadyWithActualHiddenHoldings() {
        assertNull(hiddenAssetsNotIncludedCaption(TotalBalance.Loading(7, "cad"), resources))
        assertNull(hiddenAssetsNotIncludedCaption(ready(), resources))
        assertEquals("1 hidden asset not included", hiddenAssetsNotIncludedCaption(ready(hidden = partition(null, null)), resources))
    }
}
