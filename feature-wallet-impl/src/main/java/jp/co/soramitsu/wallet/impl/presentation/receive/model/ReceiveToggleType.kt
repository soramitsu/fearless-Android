package jp.co.soramitsu.wallet.impl.presentation.receive.model

import androidx.annotation.StringRes
import jp.co.soramitsu.common.compose.component.MultiToggleItem
import jp.co.soramitsu.feature_wallet_impl.R

enum class ReceiveToggleType(@StringRes override val titleResId: Int) : MultiToggleItem {
    Receive(R.string.common_action_receive),
    Request(R.string.common_request)
}
