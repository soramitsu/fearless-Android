package jp.co.soramitsu.feature_account_impl.presentation.account

import android.view.ViewGroup
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.common.list.GroupedListHolder
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model.NetworkModel
import kotlinx.android.synthetic.main.item_account_group.view.accountGroupIcon
import kotlinx.android.synthetic.main.item_account_group.view.accountGroupName

class AccountGroupHolder(parent: ViewGroup) : GroupedListHolder(inflate(parent)) {

    fun bind(networkModel: NetworkModel) = with(containerView) {
        accountGroupIcon.setImageResource(networkModel.networkTypeUI.icon)
        accountGroupName.text = networkModel.name
    }
}

private fun inflate(parent: ViewGroup) = parent.inflateChild(R.layout.item_account_group)