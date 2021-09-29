package jp.co.soramitsu.feature_account_impl.presentation.account

import androidx.recyclerview.widget.DiffUtil
import jp.co.soramitsu.feature_account_impl.presentation.account.model.AccountModel
import jp.co.soramitsu.common.list.BaseGroupedDiffCallback
import jp.co.soramitsu.feature_account_impl.presentation.account.model.LightMetaAccountUi
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model.NetworkModel

object LightMetaAccountDiffCallback : DiffUtil.ItemCallback<LightMetaAccountUi>() {

    override fun areItemsTheSame(oldItem: LightMetaAccountUi, newItem: LightMetaAccountUi): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: LightMetaAccountUi, newItem: LightMetaAccountUi): Boolean {
        return oldItem == newItem
    }
}

