package jp.co.soramitsu.feature_onboarding_impl.presentation.importing.encryption

import android.app.Activity
import android.view.LayoutInflater
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.presentation.importing.encryption.model.CryptoTypeModel
import kotlinx.android.synthetic.main.choosed_bottom_dialog.list
import kotlinx.android.synthetic.main.choosed_bottom_dialog.titleTv

class EncryptionTypeChooserBottomSheetDialog(
    context: Activity,
    encryptionTypes: List<CryptoTypeModel>,
    itemClickListener: (CryptoType) -> Unit
) : BottomSheetDialog(context, R.style.BottomSheetDialog) {
    init {
        setContentView(LayoutInflater.from(context).inflate(R.layout.choosed_bottom_dialog, null))
        titleTv.text = context.getString(R.string.common_crypto_type)

        val adapter = EncryptionTypeListAdapter {
            itemClickListener(it)
            dismiss()
        }

        adapter.submitList(encryptionTypes)
        list.adapter = adapter
    }
}