package jp.co.soramitsu.wallet.impl.presentation.balance.list

import java.math.BigDecimal
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState

/** Exact numeric values for presentation; never parse localized displayed amounts to add them. */
data class PortfolioAsset(
    val state: AssetListItemViewState,
    val total: BigDecimal?,
    val trustedFiat: BigDecimal?,
    val fiatSymbol: String?,
    val rank: Int? = null
) {
    val key: String get() = state.canonicalAssetKey
    val isAutomaticZero: Boolean get() = total?.signum() == 0 && state.assetPreference == "auto"
}
