package jp.co.soramitsu.feature_wallet_impl.presentation.balance.list.changeAccount

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.account.AddressModel
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.feature_wallet_impl.R
import kotlinx.android.synthetic.main.item_account_chooser.view.accountChecked
import kotlinx.android.synthetic.main.item_account_chooser.view.accountIcon
import kotlinx.android.synthetic.main.item_account_chooser.view.accountTitle

class AccountsAdapter(
    private val handler: Handler,
    private val selected: AddressModel
) : ListAdapter<AddressModel, AccountHolder>(AddressModelDiffCallback) {

    interface Handler {

        fun itemClicked(account: AddressModel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountHolder {
        return AccountHolder(parent.inflateChild(R.layout.item_account_chooser))
    }

    override fun onBindViewHolder(holder: AccountHolder, position: Int) {
        val item = getItem(position)
        val isSelected = item.address == selected.address

        holder.bind(item, isSelected, handler)
    }
}

class AccountHolder(view: View) : RecyclerView.ViewHolder(view) {
    fun bind(
        accountModel: AddressModel,
        selected: Boolean,
        handler: AccountsAdapter.Handler
    ) {
        with(itemView) {
            accountTitle.text = accountModel.address
            accountIcon.setImageDrawable(accountModel.image)
            accountChecked.visibility = if (selected) View.VISIBLE else View.INVISIBLE

            setOnClickListener { handler.itemClicked(accountModel) }
        }
    }
}

object AddressModelDiffCallback : DiffUtil.ItemCallback<AddressModel>() {
    override fun areItemsTheSame(oldItem: AddressModel, newItem: AddressModel): Boolean {
        return oldItem.address == newItem.address
    }

    override fun areContentsTheSame(oldItem: AddressModel, newItem: AddressModel): Boolean {
        return true
    }
}