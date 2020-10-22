package jp.co.soramitsu.feature_wallet_impl.presentation.balance.list.changeAccount

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.common.account.AddressModel
import jp.co.soramitsu.feature_wallet_impl.R
import kotlinx.android.synthetic.main.sheet_account_chooser.accountChooserAdd
import kotlinx.android.synthetic.main.sheet_account_chooser.accountChooserList

class AccountChooserPayload(
    val accountsByNetwork: List<AddressModel>,
    val selected: AddressModel
)

class AccountChooserBottomSheetDialog(
    context: Activity,
    payload: AccountChooserPayload,
    private val clickHandler: ClickHandler
) : BottomSheetDialog(context, R.style.BottomSheetDialog), AccountsAdapter.Handler {

    interface ClickHandler {
        fun accountClicked(addressModel: AddressModel)

        fun addAccountClicked()
    }

    init {
        setContentView(LayoutInflater.from(context).inflate(R.layout.sheet_account_chooser, null))

        accountChooserAdd.setOnClickListener {
            clickHandler.addAccountClicked()
            dismiss()
        }

        accountChooserList.setHasFixedSize(true)

        val adapter = AccountsAdapter(this, payload.selected)
        accountChooserList.adapter = adapter

        adapter.submitList(payload.accountsByNetwork)
    }

    override fun itemClicked(account: AddressModel) {
        clickHandler.accountClicked(account)
        dismiss()
    }
}