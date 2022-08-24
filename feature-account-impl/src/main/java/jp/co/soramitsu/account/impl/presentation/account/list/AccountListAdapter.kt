package jp.co.soramitsu.account.impl.presentation.account.list

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ListAdapter
import jp.co.soramitsu.common.list.GroupedListHolder
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.account.impl.presentation.account.LightMetaAccountDiffCallback
import jp.co.soramitsu.account.impl.presentation.account.model.LightMetaAccountUi

class AccountsAdapter(
    private val accountItemHandler: AccountItemHandler
) : ListAdapter<LightMetaAccountUi, AccountHolder>(LightMetaAccountDiffCallback) {

    interface AccountItemHandler {

        fun optionsClicked(accountModel: LightMetaAccountUi)

        fun checkClicked(accountModel: LightMetaAccountUi)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountHolder {
        return AccountHolder(parent.inflateChild(R.layout.item_account))
    }

    override fun onBindViewHolder(holder: AccountHolder, position: Int) {
        holder.bind(getItem(position), accountItemHandler)
    }
}

class AccountHolder(view: View) : GroupedListHolder(view) {

    fun bind(
        accountModel: LightMetaAccountUi,
        handler: AccountsAdapter.AccountItemHandler
    ) {
        with(containerView) {
            findViewById<TextView>(R.id.accountTitle).text = accountModel.name

            findViewById<ImageView>(R.id.accountIcon).setImageDrawable(accountModel.picture.value)

            findViewById<ImageView>(R.id.accountCheck).visibility = if (accountModel.isSelected) View.VISIBLE else View.INVISIBLE

            setOnClickListener { handler.checkClicked(accountModel) }

            findViewById<ImageView>(R.id.accountInfo).setOnClickListener { handler.optionsClicked(accountModel) }
        }
    }
}
