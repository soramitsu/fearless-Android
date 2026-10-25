package jp.co.soramitsu.wallet.impl.presentation.balance.list

import jp.co.soramitsu.common.compose.component.MultiToggleItem
import jp.co.soramitsu.feature_wallet_impl.R

/** The saved portfolio grouping; exact asset identities are unchanged by either lens. */
enum class PortfolioLens(override val titleResId: Int) : MultiToggleItem {
    ByToken(R.string.portfolio_by_token),
    ByNetwork(R.string.portfolio_by_network)
}
