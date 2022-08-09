package jp.co.soramitsu.featurewalletimpl.presentation.balance.detail

import android.content.Context
import android.os.Bundle
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.item
import jp.co.soramitsu.featureaccountapi.presentation.actions.CopyCallback

class BalanceDetailOptionsBottomSheet(
    context: Context,
    val address: String,
    private val onExportAccount: () -> Unit,
    private val onSwitchNode: () -> Unit,
    private val onCopy: CopyCallback
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

        item(R.drawable.ic_copy_24, R.string.common_copy_address) {
            onCopy(address)
        }
    }
}
