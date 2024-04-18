package jp.co.soramitsu.wallet.impl.presentation.balance.detail

import android.content.Context
import android.os.Bundle
import jp.co.soramitsu.account.api.presentation.actions.CopyCallback
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.item

class BalanceDetailOptionsBottomSheet(
    context: Context,
    val address: String,
    private val onExportAccount: () -> Unit,
    private val onSwitchNode: () -> Unit,
    private val onCopy: CopyCallback,
    private val onClaimReward: (() -> Unit)?
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

        onClaimReward?.let { claimReward ->
            item(R.drawable.ic_info_white_24, R.string.pool_claim_reward) {
                claimReward.invoke()
            }
        }
    }
}

class BalanceDetailEthereumOptionsBottomSheet(
    context: Context,
    val address: String,
    private val onExportAccount: () -> Unit,
    private val onCopy: CopyCallback
) : FixedListBottomSheet(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.account_option)

        item(R.drawable.ic_share_arrow_white_24, R.string.account_export) {
            onExportAccount()
        }

        item(R.drawable.ic_copy_24, R.string.common_copy_address) {
            onCopy(address)
        }
    }
}