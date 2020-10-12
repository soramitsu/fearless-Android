package jp.co.soramitsu.feature_account_impl.presentation.node.list.accounts

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.node.list.accounts.model.AccountByNetworkModel
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model.NetworkModel
import kotlinx.android.synthetic.main.bottom_sheet_account_chooser.accountsRv
import kotlinx.android.synthetic.main.bottom_sheet_account_chooser.addImg
import kotlinx.android.synthetic.main.bottom_sheet_account_chooser.titleTv

class AccountChooserPayload(val accountsByNetwork: List<AccountByNetworkModel>, val network: NetworkModel.NetworkTypeUI)

class AccountChooserBottomSheetDialog(
    context: Activity,
    accountChooserPayload: AccountChooserPayload,
    private val clickHandler: ClickHandler
) : BottomSheetDialog(context, R.style.BottomSheetDialog), AccountsAdapter.AccountItemHandler {

    interface ClickHandler {

        fun accountClicked(account: AccountByNetworkModel)

        fun addAccountClicked(networkType: Node.NetworkType)
    }

    init {
        setContentView(LayoutInflater.from(context).inflate(R.layout.bottom_sheet_account_chooser, null))

        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    behavior.state = BottomSheetBehavior.STATE_COLLAPSED
                }
            }
        })

        titleTv.text = context.getString(R.string.profile_accounts_title)

        addImg.setOnClickListener {
            clickHandler.addAccountClicked(accountChooserPayload.network.networkType)
            dismiss()
        }

        val adapter = AccountsAdapter(this)

        adapter.submitList(accountChooserPayload.accountsByNetwork)
        accountsRv.adapter = adapter
    }

    override fun itemClicked(account: AccountByNetworkModel) {
        clickHandler.accountClicked(account)
        dismiss()
    }
}