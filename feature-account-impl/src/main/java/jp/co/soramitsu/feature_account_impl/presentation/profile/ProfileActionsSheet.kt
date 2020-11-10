package jp.co.soramitsu.feature_account_impl.presentation.profile

import android.content.Context
import android.os.Bundle
import jp.co.soramitsu.common.view.bottomSheet.CopyCallback
import jp.co.soramitsu.common.view.bottomSheet.ExternalActionsSheet
import jp.co.soramitsu.common.view.bottomSheet.ExternalViewCallback
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_impl.R

class ProfileActionsSheet(
    context: Context,
    address: String,
    networkType: Node.NetworkType,
    onCopy: CopyCallback,
    onExternalView: ExternalViewCallback,
    private val onOpenAccounts: () -> Unit
) : ExternalActionsSheet(
    context,
    Payload(R.string.profile_title, R.string.common_copy_address, address, networkType),
    onCopy, onExternalView
) {
    override fun onCreate(savedInstanceState: Bundle?) {
        item(R.drawable.ic_user_24, R.string.profile_accounts_title) {
            onOpenAccounts()
        }

        super.onCreate(savedInstanceState)
    }
}