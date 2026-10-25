package jp.co.soramitsu.account.api.presentation.account

import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatFiat

enum class PortfolioValuationStatus { Loading, AwaitingPrices, Unavailable, Partial, Complete }

/** Formatting retains the exact source instance; later global labels cannot revalue this result. */
data class PortfolioBalancePresentation(
    val source: TotalBalance,
    val currencyId: String?,
    val value: String?,
    val status: PortfolioValuationStatus,
    val change: ChangeBalanceViewState?,
    val includesHidden: Boolean,
    val hiddenExcludedCount: Int
)

/** Only complete totals or explicitly partial subtotals are formatted; absence never becomes zero. */
fun presentTotalBalance(total: TotalBalance, includeHidden: Boolean): PortfolioBalancePresentation {
    if (total !is TotalBalance.Ready) {
        return PortfolioBalancePresentation(
            source = total,
            currencyId = (total as? TotalBalance.Loading)?.currencyId,
            value = null,
            status = if (total == TotalBalance.Unavailable) PortfolioValuationStatus.Unavailable else PortfolioValuationStatus.Loading,
            change = null,
            includesHidden = false,
            hiddenExcludedCount = 0
        )
    }
    val partition = if (includeHidden) total.allHoldings else total.shown
    val status = when {
        partition.amount != null -> PortfolioValuationStatus.Complete
        !total.quotesReady -> PortfolioValuationStatus.AwaitingPrices
        partition.knownSubtotal != null -> PortfolioValuationStatus.Partial
        else -> PortfolioValuationStatus.Unavailable
    }
    val value = (partition.amount ?: partition.knownSubtotal)?.formatFiat(total.fiatSymbol)
    val change = if (partition.amount != null && partition.change != null && partition.rateChange != null) {
        ChangeBalanceViewState(
            percentChange = partition.rateChange.formatAsChange(),
            fiatChange = partition.change.abs().formatFiat(total.fiatSymbol)
        )
    } else null
    return PortfolioBalancePresentation(
        source = total,
        currencyId = total.currencyId,
        value = value,
        status = status,
        change = change,
        includesHidden = includeHidden && total.hidden.holdingCount > 0,
        hiddenExcludedCount = if (includeHidden) 0 else total.hidden.holdingCount
    )
}
