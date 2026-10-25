package jp.co.soramitsu.account.api.presentation.account

import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.resources.ResourceManager

/** Management cards include hidden holdings and label partial or unavailable valuations. */
fun WalletItemViewState.withAllHoldings(
    total: TotalBalance,
    resourceManager: ResourceManager
): WalletItemViewState = withBalance(total, resourceManager, includeHidden = true)

/** Portfolio cards use only the captured currency and exclude hidden holdings. */
fun WalletItemViewState.withPortfolioBalance(
    total: TotalBalance,
    resourceManager: ResourceManager
): WalletItemViewState = withBalance(total, resourceManager, includeHidden = false)

private fun WalletItemViewState.withBalance(
    total: TotalBalance,
    resourceManager: ResourceManager,
    includeHidden: Boolean
): WalletItemViewState {
    val admitted = when (total) {
        is TotalBalance.Ready -> total.takeIf { it.meta.id == id } ?: TotalBalance.Unavailable
        is TotalBalance.Loading -> total.takeIf { it.metaId == id } ?: TotalBalance.Unavailable
        else -> total
    }
    val presentation = presentTotalBalance(admitted, includeHidden)
    return copy(
        balance = presentation.value,
        assetSymbol = null,
        balanceCaption = portfolioBalanceCaption(presentation, resourceManager),
        changeBalanceViewState = presentation.change
    )
}

/** Status is always visible beside a subtotal, including while quotes are still loading. */
fun portfolioBalanceCaption(presentation: PortfolioBalancePresentation, resourceManager: ResourceManager): String? {
    val status = when (presentation.status) {
        PortfolioValuationStatus.Loading -> resourceManager.getString(R.string.portfolio_balance_loading)
        PortfolioValuationStatus.AwaitingPrices -> listOfNotNull(
            if (presentation.value != null) resourceManager.getString(R.string.portfolio_balance_partial) else null,
            resourceManager.getString(R.string.portfolio_prices_loading)
        ).joinToString(" · ")
        PortfolioValuationStatus.Unavailable -> resourceManager.getString(R.string.portfolio_balance_unavailable)
        PortfolioValuationStatus.Partial -> resourceManager.getString(R.string.portfolio_balance_partial)
        PortfolioValuationStatus.Complete -> null
    }
    val scope = when {
        presentation.includesHidden -> resourceManager.getString(R.string.portfolio_balance_incl_hidden)
        presentation.hiddenExcludedCount > 0 -> resourceManager.getQuantityString(
            R.plurals.portfolio_hidden_assets_not_included,
            presentation.hiddenExcludedCount,
            presentation.hiddenExcludedCount
        )
        else -> null
    }
    return listOfNotNull(status, scope).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** The hidden-holding count is read only from a completed captured portfolio. */
fun hiddenAssetsNotIncludedCaption(total: TotalBalance, resourceManager: ResourceManager): String? {
    val count = (total as? TotalBalance.Ready)?.hidden?.holdingCount?.takeIf { it > 0 } ?: return null
    return resourceManager.getQuantityString(R.plurals.portfolio_hidden_assets_not_included, count, count)
}
