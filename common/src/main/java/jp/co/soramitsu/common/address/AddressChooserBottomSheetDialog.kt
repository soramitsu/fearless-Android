package jp.co.soramitsu.common.address

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.recyclerview.widget.DiffUtil
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.ClickHandler
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListSheetAdapter
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.HolderCreator
import kotlinx.android.synthetic.main.item_address_chooser.view.accountChecked
import kotlinx.android.synthetic.main.item_address_chooser.view.accountIcon
import kotlinx.android.synthetic.main.item_address_chooser.view.accountTitle

class AddressChooserBottomSheetDialog(
    context: Context,
    payload: Payload<AddressModel>,
    clickHandler: ClickHandler<AddressModel>,
    @StringRes val title: Int
) : DynamicListBottomSheet<AddressModel>(
    context, payload, AddressModelDiffCallback, clickHandler
) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(title)
    }

    override fun holderCreator(): HolderCreator<AddressModel> = { parent ->
        AddressModelHolder(parent.inflateChild(R.layout.item_address_chooser))
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
            accountTitle.text = item.name ?: item.address
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
