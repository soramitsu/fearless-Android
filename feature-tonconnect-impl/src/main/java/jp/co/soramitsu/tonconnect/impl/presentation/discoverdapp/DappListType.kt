package jp.co.soramitsu.tonconnect.impl.presentation.discoverdapp

import androidx.annotation.StringRes
import jp.co.soramitsu.common.compose.component.MultiToggleItem
import jp.co.soramitsu.feature_wallet_impl.R

enum class DappListType(@StringRes override val titleResId: Int) : MultiToggleItem {
    Discover(R.string.tc_discover_dapp),
    Connected(R.string.tc_connected_dapp)
}