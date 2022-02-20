package jp.co.soramitsu.feature_account_impl.presentation.account.details

import android.content.Context
import android.os.Bundle
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.item
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

class ImportChainAccountActionsSheet(
    context: Context,
    val payload: ImportChainAccountsPayload,
    private val onCreateAccount: (chainId: ChainId, metaId: Long) -> Unit,
    private val onImportAccount: (chainId: ChainId, metaId: Long) -> Unit
) : FixedListBottomSheet(context = context) {
    override fun onCreate(savedInstanceState: Bundle?) {

        setTitle(
            context.getString(R.string.replace_account_template, payload.chainName)
        )

        item(R.drawable.ic_plus_circle, R.string.create_new_account) {
            onCreateAccount(payload.chainId, payload.metaId)
        }

        item(R.drawable.ic_change_account, R.string.already_have_account) {
            onImportAccount(payload.chainId, payload.metaId)
        }

        super.onCreate(savedInstanceState)
    }
}
