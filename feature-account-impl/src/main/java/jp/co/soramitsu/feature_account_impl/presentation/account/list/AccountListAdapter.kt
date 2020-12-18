package jp.co.soramitsu.feature_account_impl.presentation.account.list

import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.account.AccountGroupHolder
import jp.co.soramitsu.feature_account_impl.presentation.account.model.AccountModel
import jp.co.soramitsu.feature_account_impl.presentation.account.AccountsDiffCallback
import jp.co.soramitsu.common.list.GroupedListAdapter
import jp.co.soramitsu.common.list.GroupedListHolder
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model.NetworkModel
import kotlinx.android.synthetic.main.item_account.view.accountAddress
import kotlinx.android.synthetic.main.item_account.view.accountCheck
import kotlinx.android.synthetic.main.item_account.view.accountIcon
import kotlinx.android.synthetic.main.item_account.view.accountInfo
import kotlinx.android.synthetic.main.item_account.view.accountTitle

class AccountsAdapter(
    private val accountItemHandler: AccountItemHandler
) : GroupedListAdapter<NetworkModel, AccountModel>(AccountsDiffCallback) {
    interface AccountItemHandler {
        fun infoClicked(accountModel: AccountModel)

        fun checkClicked(accountModel: AccountModel)
    }

    private var selectedItem: AccountModel? = null

    fun updateSelectedAccount(newSelection: AccountModel) {
        val positionToHide = selectedItem?.let { selected ->
            findIndexOfElement<AccountModel> { selected.address == it.address }
        }

        val positionToShow = findIndexOfElement<AccountModel> {
            newSelection.address == it.address
        }

        selectedItem = newSelection

        positionToHide?.let { notifyItemChanged(it) }
        notifyItemChanged(positionToShow)
    }

    override fun createGroupViewHolder(parent: ViewGroup): GroupedListHolder {
        return AccountGroupHolder(parent)
    }

    override fun createChildViewHolder(parent: ViewGroup): GroupedListHolder {
        return AccountHolder(parent.inflateChild(R.layout.item_account))
    }

    override fun bindGroup(holder: GroupedListHolder, group: NetworkModel) {
        (holder as AccountGroupHolder).bind(group)
    }

    override fun bindChild(holder: GroupedListHolder, child: AccountModel) {
        val isChecked = child.address == selectedItem?.address

        (holder as AccountHolder).bind(child, accountItemHandler, isChecked)
    }
}

class AccountHolder(view: View) : GroupedListHolder(view) {
    fun bind(
        accountModel: AccountModel,
        handler: AccountsAdapter.AccountItemHandler,
        isChecked: Boolean
    ) {
        with(containerView) {
            accountTitle.text = accountModel.name ?: ""
            accountAddress.text = accountModel.address
            accountIcon.setImageDrawable(accountModel.image)

            accountCheck.visibility = if (isChecked) View.VISIBLE else View.INVISIBLE

            setOnClickListener { handler.checkClicked(accountModel) }

            accountInfo.setOnClickListener { handler.infoClicked(accountModel) }
        }
    }
}