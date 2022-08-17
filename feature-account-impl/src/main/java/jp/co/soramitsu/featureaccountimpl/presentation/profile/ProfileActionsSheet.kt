package jp.co.soramitsu.featureaccountimpl.presentation.profile

import android.content.Context
import android.os.Bundle
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.item
import jp.co.soramitsu.featureaccountapi.presentation.actions.CopyCallback
import jp.co.soramitsu.featureaccountapi.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.featureaccountapi.presentation.actions.ExternalActionsSheet
import jp.co.soramitsu.featureaccountapi.presentation.actions.ExternalViewCallback
import jp.co.soramitsu.feature_account_impl.R

class ProfileActionsSheet(
    context: Context,
    content: ExternalAccountActions.Payload,
    onCopy: CopyCallback,
    onExternalView: ExternalViewCallback,
    private val onOpenAccounts: () -> Unit
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
        item(R.drawable.ic_user_24, R.string.profile_accounts_title) {
            onOpenAccounts()
        }

        super.onCreate(savedInstanceState)
    }
}
