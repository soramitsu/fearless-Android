package jp.co.soramitsu.feature_wallet_impl.presentation.beacon

import android.content.Context
import android.os.Bundle
import it.airgap.beaconsdk.message.PermissionBeaconRequest
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.item
import jp.co.soramitsu.feature_wallet_impl.R

class PermissionRequestBottomSheet(
    context: Context,
    private val request: PermissionBeaconRequest,
    private val onApprove: (PermissionBeaconRequest) -> Unit,
    private val onDeny: () -> Unit
) : FixedListBottomSheet(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(request.appMetadata.name)

        item(titleRes = R.string.common_connect) {
            onApprove(request)
        }

        item(titleRes = R.string.common_cancel) {
            onDeny()
        }

        setCancelable(false)
    }
}
