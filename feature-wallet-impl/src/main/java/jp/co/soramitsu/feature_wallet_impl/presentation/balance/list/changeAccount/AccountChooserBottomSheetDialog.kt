package jp.co.soramitsu.feature_wallet_impl.presentation.balance.list.changeAccount

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.DiffUtil
import jp.co.soramitsu.common.account.AddressModel
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.ClickHandler
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListSheetAdapter
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.HolderCreator
import jp.co.soramitsu.feature_wallet_impl.R
import kotlinx.android.synthetic.main.item_account_chooser.view.accountChecked
import kotlinx.android.synthetic.main.item_account_chooser.view.accountIcon
import kotlinx.android.synthetic.main.item_account_chooser.view.accountTitle

class AccountChooserBottomSheetDialog(
    context: Activity,
    payload: Payload<AddressModel>,
    clickHandler: ClickHandler<AddressModel>
) : DynamicListBottomSheet<AddressModel>(
    context, payload, AddressModelDiffCallback, clickHandler
) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.profile_accounts_title)
    }

    override fun holderCreator(): HolderCreator<AddressModel> = { parent ->
        AddressModelHolder(parent.inflateChild(R.layout.item_account_chooser))
    }
}

private class AddressModelHolder(parent: View) : DynamicListSheetAdapter.Holder<AddressModel>(parent) {

    override fun bind(
        item: AddressModel,
        isSelected: Boolean,
        handler: DynamicListSheetAdapter.Handler<AddressModel>
    ) {
        super.bind(item, isSelected, handler)

        with(itemView) {
            accountTitle.text = item.address
            accountIcon.setImageDrawable(item.image)
            accountChecked.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
        }
    }
}

private object AddressModelDiffCallback : DiffUtil.ItemCallback<AddressModel>() {
    override fun areItemsTheSame(oldItem: AddressModel, newItem: AddressModel): Boolean {
        return oldItem.address == newItem.address
    }

    override fun areContentsTheSame(oldItem: AddressModel, newItem: AddressModel): Boolean {
        return true
    }
}