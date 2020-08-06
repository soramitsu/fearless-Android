package jp.co.soramitsu.feature_onboarding_impl.presentation.import_account.dialog

import android.app.Activity
import android.view.LayoutInflater
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.NetworkType
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.presentation.import_account.dialog.model.EncryptionTypeChooserDialogData
import jp.co.soramitsu.feature_onboarding_impl.presentation.import_account.dialog.model.NetworkTypeChooserDialogData
import kotlinx.android.synthetic.main.choosed_bottom_dialog.list
import kotlinx.android.synthetic.main.choosed_bottom_dialog.titleTv
import java.math.BigInteger

class NetworkTypeChooserBottomSheetDialog(
    context: Activity,
    networkTypeChooserDialogData: NetworkTypeChooserDialogData,
    itemClickListener: (Node) -> Unit
) : BottomSheetDialog(context, R.style.BottomSheetDialog) {
    init {
        setContentView(LayoutInflater.from(context).inflate(R.layout.choosed_bottom_dialog, null))
        titleTv.text = "Network type"

        val adapter = NodeListAdapter(
            networkTypeChooserDialogData.selectedNode
        ) {
            itemClickListener(it)
            dismiss()
        }
        adapter.submitList(networkTypeChooserDialogData.nodes)
        list.adapter = adapter
    }
}