package jp.co.soramitsu.feature_wallet_impl.presentation.balance.detail

import android.content.Context
import android.os.Bundle
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.item

class BalanceDetailOptionsBottomSheet(
    context: Context,
    private val onExportAccount: () -> Unit,
    private val onSwitchNode: () -> Unit
) : FixedListBottomSheet(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.account_option)

        item(R.drawable.ic_share_arrow_white_24, R.string.account_export) {
            onExportAccount()
        }

        item(R.drawable.ic_refresh_white_24, R.string.switch_node) {
            onSwitchNode()
        }
    }
}
