package jp.co.soramitsu.feature_account_impl.presentation.editAccounts

import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.common.accountManagment.AccountGroupHolder
import jp.co.soramitsu.feature_account_impl.presentation.common.accountManagment.AccountsDiffCallback
import jp.co.soramitsu.feature_account_impl.presentation.common.accountManagment.AccountModel
import jp.co.soramitsu.feature_account_impl.presentation.common.groupedList.GroupedListAdapter
import jp.co.soramitsu.feature_account_impl.presentation.common.groupedList.GroupedListHolder
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.AccountListing
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model.NetworkModel
import kotlinx.android.synthetic.main.item_edit_account.view.accountAddress
import kotlinx.android.synthetic.main.item_edit_account.view.accountDelete
import kotlinx.android.synthetic.main.item_edit_account.view.accountIcon
import kotlinx.android.synthetic.main.item_edit_account.view.accountTitle

class EditAccountsAdapter(
    private val accountItemHandler: EditAccountItemHandler
) : GroupedListAdapter<NetworkModel, AccountModel>(AccountsDiffCallback) {
    private var selectedItem: AccountModel? = null

    fun submitListing(accountListing: AccountListing) {
        selectedItem = accountListing.selectedAccount

        submitList(accountListing.groupedAccounts)
    }

    interface EditAccountItemHandler {
        fun deleteClicked(accountModel: AccountModel)
    }

    override fun createGroupViewHolder(parent: ViewGroup): GroupedListHolder {
        return AccountGroupHolder(parent)
    }

    override fun createChildViewHolder(parent: ViewGroup): GroupedListHolder {
        return EditAccountHolder(parent.inflateChild(R.layout.item_edit_account))
    }

    override fun bindGroup(holder: GroupedListHolder, group: NetworkModel) {
        (holder as AccountGroupHolder).bind(group)
    }

    override fun bindChild(holder: GroupedListHolder, child: AccountModel) {
        val isChecked = child.address == selectedItem!!.address

        (holder as EditAccountHolder).bind(child, accountItemHandler, isChecked)
    }
}

class EditAccountHolder(view: View) : GroupedListHolder(view) {
    fun bind(
        accountModel: AccountModel,
        handler: EditAccountsAdapter.EditAccountItemHandler,
        isChecked: Boolean
    ) {
        with(containerView) {
            accountTitle.text = accountModel.name ?: ""
            accountAddress.text = accountModel.address
            accountIcon.setImageDrawable(accountModel.image)

            val iconRes = if (isChecked) R.drawable.ic_checkmark_white_24 else R.drawable.ic_delete_symbol
            accountDelete.setImageResource(iconRes)

            accountDelete.setOnClickListener { handler.deleteClicked(accountModel) }
        }
    }
}