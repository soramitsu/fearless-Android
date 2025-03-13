package jp.co.soramitsu.account.impl.presentation.account.details

import android.content.Context
import android.os.Bundle
import jp.co.soramitsu.account.api.presentation.actions.CopyCallback
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.account.api.presentation.actions.ExternalActionsSheet
import jp.co.soramitsu.account.api.presentation.actions.ExternalViewCallback
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.item
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

class WalletAccountActionsSheet(
    context: Context,
    val content: Payload,
    onCopy: CopyCallback,
    onExternalView: ExternalViewCallback,
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
    class Payload(
        value: String,
        explorers: Map<Chain.Explorer.Type, String>,
        val chainId: ChainId? = null,
        val chainName: String? = null,
        val canSwitchNode: Boolean = true
    ): ExternalAccountActions.Payload(value, explorers)

    override fun onCreate(savedInstanceState: Bundle?) {
        content.chainId?.let { chainId ->
            item(R.drawable.ic_share_arrow_white_24, R.string.account_export) {
                onExportAccount(chainId)
            }

            if (content.canSwitchNode) {
                item(R.drawable.ic_refresh_white_24, R.string.switch_node) {
                    onSwitchNode(chainId)
                }
            }
        }

        super.onCreate(savedInstanceState)
        setTitle(content.chainName)
    }
}
