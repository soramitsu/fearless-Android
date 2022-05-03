package jp.co.soramitsu.feature_account_impl.presentation.account.details

import android.content.Context
import android.os.Bundle
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.item
import jp.co.soramitsu.feature_account_api.presentation.actions.CopyCallback
import jp.co.soramitsu.feature_account_api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_account_api.presentation.actions.ExternalActionsSheet
import jp.co.soramitsu.feature_account_api.presentation.actions.ExternalViewCallback
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

class WalletAccountActionsSheet(
    context: Context,
    val content: ExternalAccountActions.Payload,
    onCopy: CopyCallback,
    onExternalView: ExternalViewCallback,
    private val onReplace: (chainId: ChainId) -> Unit,
    private val onExportAccount: (chainId: ChainId) -> Unit,
    private val onSwitchNode: (chainId: ChainId) -> Unit
) : ExternalActionsSheet(
    context = context,
    payload = Payload(
        copyLabel = R.string.common_copy_address,
        content = content
    ),
    onCopy = onCopy,
    onViewExternal = onExternalView
) {
    override fun onCreate(savedInstanceState: Bundle?) {
        content.chainId?.let { chainId ->
            item(R.drawable.ic_share_arrow_white_24, R.string.account_export) {
                onExportAccount(chainId)
            }

            item(R.drawable.ic_change_account, R.string.replace_account) {
                onReplace(chainId)
            }

            item(jp.co.soramitsu.common.R.drawable.ic_refresh_white_24, R.string.switch_node) {
                onSwitchNode(chainId)
            }
        }

        super.onCreate(savedInstanceState)
        setTitle(content.chainName)
    }
}
