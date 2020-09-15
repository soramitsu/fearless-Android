package jp.co.soramitsu.feature_account_impl.presentation.accounts

import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.accounts.model.AccountModel
import jp.co.soramitsu.feature_account_impl.presentation.common.groupedList.BaseGroupedDiffCallback
import jp.co.soramitsu.feature_account_impl.presentation.common.groupedList.GroupedListAdapter
import jp.co.soramitsu.feature_account_impl.presentation.common.groupedList.GroupedListHolder
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model.NetworkModel
import kotlinx.android.synthetic.main.item_account.view.accountAddress
import kotlinx.android.synthetic.main.item_account.view.accountCheck
import kotlinx.android.synthetic.main.item_account.view.accountIcon
import kotlinx.android.synthetic.main.item_account.view.accountInfo
import kotlinx.android.synthetic.main.item_account.view.accountTitle
import kotlinx.android.synthetic.main.item_account_group.view.accountGroupIcon
import kotlinx.android.synthetic.main.item_account_group.view.accountGroupName

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
        return AccountGroupHolder(inflate(parent, R.layout.item_account_group))
    }

    override fun createChildViewHolder(parent: ViewGroup): GroupedListHolder {
        return AccountHolder(inflate(parent, R.layout.item_account))
    }

    override fun bindGroup(holder: GroupedListHolder, group: NetworkModel) {
        (holder as AccountGroupHolder).bind(group)
    }

    override fun bindChild(holder: GroupedListHolder, child: AccountModel) {
        val isChecked = child.address == selectedItem?.address

        (holder as AccountHolder).bind(child, accountItemHandler, isChecked)
    }
}

class AccountGroupHolder(view: View) : GroupedListHolder(view) {
    fun bind(networkModel: NetworkModel) = with(containerView) {
        accountGroupIcon.setImageResource(networkModel.networkTypeUI.smallIcon)
        accountGroupName.text = networkModel.name
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

private object AccountsDiffCallback :
    BaseGroupedDiffCallback<NetworkModel, AccountModel>(NetworkModel::class.java) {
    override fun areGroupItemsTheSame(oldItem: NetworkModel, newItem: NetworkModel): Boolean {
        return oldItem.name == newItem.name
    }

    override fun areGroupContentsTheSame(oldItem: NetworkModel, newItem: NetworkModel): Boolean {
        return oldItem == newItem
    }

    override fun areChildItemsTheSame(oldItem: AccountModel, newItem: AccountModel): Boolean {
        return oldItem.address == newItem.address
    }

    override fun areChildContentsTheSame(oldItem: AccountModel, newItem: AccountModel): Boolean {
        return oldItem == newItem
    }
}