package jp.co.soramitsu.feature_onboarding_impl.presentation.import_account.dialog

import android.app.Activity
import android.view.LayoutInflater
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.presentation.import_account.dialog.model.EncryptionTypeChooserDialogData
import kotlinx.android.synthetic.main.choosed_bottom_dialog.list
import kotlinx.android.synthetic.main.choosed_bottom_dialog.titleTv

class EncryptionTypeChooserBottomSheetDialog(
    context: Activity,
    encryptionTypeChooserDialogData: EncryptionTypeChooserDialogData,
    itemClickListener: (CryptoType) -> Unit
) : BottomSheetDialog(context, R.style.BottomSheetDialog) {
    init {
        setContentView(LayoutInflater.from(context).inflate(R.layout.choosed_bottom_dialog, null))
        titleTv.text = "Encryption type"

        val adapter = EncryptionTypeListAdapter(
            encryptionTypeChooserDialogData.selectedEncryptionType
        ) {
            itemClickListener(it)
            dismiss()
        }
        adapter.submitList(encryptionTypeChooserDialogData.encryptionTypes)
        list.adapter = adapter
    }
}