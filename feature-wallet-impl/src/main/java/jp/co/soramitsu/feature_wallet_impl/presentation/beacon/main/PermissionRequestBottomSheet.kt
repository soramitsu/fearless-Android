package jp.co.soramitsu.feature_wallet_impl.presentation.beacon.main

import android.content.Context
import android.os.Bundle
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.item
import jp.co.soramitsu.feature_wallet_impl.R

class PermissionRequestBottomSheet(
    context: Context,
    private val dAppName: String,
    private val onApprove: () -> Unit,
    private val onDeny: () -> Unit
) : FixedListBottomSheet(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(dAppName)

        item(titleRes = R.string.common_connect) {
            onApprove()
        }

        item(titleRes = R.string.common_cancel) {
            onDeny()
        }

        setCancelable(false)
    }
}
