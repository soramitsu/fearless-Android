package jp.co.soramitsu.feature_account_impl.presentation.node.list.accounts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.node.list.accounts.model.AccountByNetworkModel
import kotlinx.android.synthetic.main.item_account_by_network.view.accountIcon
import kotlinx.android.synthetic.main.item_account_by_network.view.accountTitle

class AccountsAdapter(
    private val accountItemHandler: AccountItemHandler
) : ListAdapter<AccountByNetworkModel, AccountHolder>(AccountDiffCallback) {

    interface AccountItemHandler {

        fun itemClicked(account: AccountByNetworkModel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountHolder {
        return AccountHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_account_by_network, parent, false))
    }

    override fun onBindViewHolder(holder: AccountHolder, position: Int) {
        holder.bind(getItem(position), accountItemHandler)
    }
}

class AccountHolder(view: View) : RecyclerView.ViewHolder(view) {
    fun bind(
        accountModel: AccountByNetworkModel,
        handler: AccountsAdapter.AccountItemHandler
    ) {
        with(itemView) {
            accountTitle.text = accountModel.name ?: ""
            accountIcon.setImageDrawable(accountModel.addressModel.image)

            setOnClickListener { handler.itemClicked(accountModel) }
        }
    }
}

object AccountDiffCallback : DiffUtil.ItemCallback<AccountByNetworkModel>() {
    override fun areItemsTheSame(oldItem: AccountByNetworkModel, newItem: AccountByNetworkModel): Boolean {
        return oldItem.accountAddress == newItem.accountAddress
    }

    override fun areContentsTheSame(oldItem: AccountByNetworkModel, newItem: AccountByNetworkModel): Boolean {
        return oldItem == newItem
    }
}