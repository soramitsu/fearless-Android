package jp.co.soramitsu.feature_account_impl.presentation.account.list

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import jp.co.soramitsu.common.list.GroupedListHolder
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.account.LightMetaAccountDiffCallback
import jp.co.soramitsu.feature_account_impl.presentation.account.model.LightMetaAccountUi
import kotlinx.android.synthetic.main.item_account.view.accountCheck
import kotlinx.android.synthetic.main.item_account.view.accountIcon
import kotlinx.android.synthetic.main.item_account.view.accountInfo
import kotlinx.android.synthetic.main.item_account.view.accountTitle

class AccountsAdapter(
    private val accountItemHandler: AccountItemHandler
) : ListAdapter<LightMetaAccountUi, AccountHolder>(LightMetaAccountDiffCallback) {

    interface AccountItemHandler {

        fun infoClicked(accountModel: LightMetaAccountUi)

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
        handler: AccountsAdapter.AccountItemHandler,
    ) {
        with(containerView) {
            accountTitle.text = accountModel.name
            accountIcon.setImageDrawable(accountModel.picture.value)

            accountCheck.visibility = if (accountModel.isSelected) View.VISIBLE else View.INVISIBLE

            setOnClickListener { handler.checkClicked(accountModel) }

            accountInfo.setOnClickListener { handler.infoClicked(accountModel) }
        }
    }
}
