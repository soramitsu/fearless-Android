package jp.co.soramitsu.wallet.impl.presentation.manageassets

import jp.co.soramitsu.common.compose.component.MultiToggleItem
import jp.co.soramitsu.feature_wallet_impl.R

enum class ManageAssetFilter(override val titleResId: Int) : MultiToggleItem {
    All(R.string.portfolio_filter_all), Shown(R.string.portfolio_filter_shown), Hidden(R.string.portfolio_filter_hidden);

    fun accepts(asset: ManageAssetItemState): Boolean = when (this) {
        All -> true
        Shown -> asset.isChecked
        Hidden -> asset.preference == "hidden"
    }
}
