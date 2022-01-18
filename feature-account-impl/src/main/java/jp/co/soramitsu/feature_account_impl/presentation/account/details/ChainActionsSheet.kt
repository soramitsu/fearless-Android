package jp.co.soramitsu.feature_account_impl.presentation.account.details

import android.content.Context
import android.os.Bundle
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.item
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

open class ChainActionsSheet(
    context: Context,
    private val payload: Payload,
    private val onSwitchNode: (String) -> Unit
) : FixedListBottomSheet(context) {

    data class Payload(val chainId: ChainId, val chainName: String, val address: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(payload.chainName)

        item(R.drawable.ic_refresh_white_24, R.string.switch_node) {
            onSwitchNode(payload.chainId)
        }
    }
}
