package jp.co.soramitsu.feature_account_impl.presentation.view.advanced.encryption

import android.content.Context
import android.view.LayoutInflater
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.encryption.model.CryptoTypeModel
import kotlinx.android.synthetic.main.bottom_sheet_encryption_type_chooser.encryptionRv
import kotlinx.android.synthetic.main.bottom_sheet_encryption_type_chooser.titleTv

class EncryptionChooserPayload(val cryptoTypes: List<CryptoTypeModel>, val selectedType: CryptoTypeModel)

class EncryptionTypeChooserBottomSheetDialog(
    context: Context,
    payload: EncryptionChooserPayload,
    val onClicked: (CryptoTypeModel) -> Unit
) : BottomSheetDialog(context, R.style.BottomSheetDialog),
    EncryptionTypeListAdapter.EncryptionItemHandler {

    init {
        setContentView(LayoutInflater.from(context).inflate(R.layout.bottom_sheet_encryption_type_chooser, null))
        titleTv.text = context.getString(R.string.common_crypto_type)

        val adapter = EncryptionTypeListAdapter(this, payload.selectedType)

        adapter.submitList(payload.cryptoTypes)
        encryptionRv.adapter = adapter
        encryptionRv.layoutManager = LinearLayoutManager(context)
    }

    override fun encryptionClicked(encryption: CryptoTypeModel) {
        onClicked(encryption)
        dismiss()
    }
}