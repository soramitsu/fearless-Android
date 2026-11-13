package jp.co.soramitsu.account.impl.presentation.importing

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.utils.makeInvisible
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.ClickHandler
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListSheetAdapter
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.HolderCreator
import jp.co.soramitsu.core.crypto.SubstratePasswordRule
import jp.co.soramitsu.feature_account_impl.R

/** One account the typed words and path open: the app that makes it and its address. */
data class SubstratePasswordAccountModel(
    val rule: SubstratePasswordRule,
    val title: String,
    val address: String
)

/**
 * Asks which Substrate account to import when an earlier browser extension made a different account
 * from the path's password (SubstratePasswordRule). The account every Fearless app opens comes first.
 */
class SubstratePasswordChooserBottomSheetDialog(
    context: Context,
    payload: Payload<SubstratePasswordAccountModel>,
    onClicked: ClickHandler<SubstratePasswordAccountModel>
) : DynamicListBottomSheet<SubstratePasswordAccountModel>(
    context,
    payload,
    SubstratePasswordAccountCallback,
    onClicked,
    expandedOnStart = true
) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.substrate_password_choice_title)
        // The explanation sits under the title, above the accounts.
        val title = findViewById<TextView>(R.id.dynamicListSheetTitle) ?: return
        val container = title.parent as? LinearLayout ?: return
        val description = container.inflateChild(R.layout.view_substrate_password_choice_description) as TextView
        description.setText(R.string.substrate_password_choice_description)
        container.addView(description, container.indexOfChild(title) + 1)
    }

    override fun holderCreator(): HolderCreator<SubstratePasswordAccountModel> = {
        SubstratePasswordAccountViewHolder(it.inflateChild(R.layout.item_substrate_password_account))
    }
}

class SubstratePasswordAccountViewHolder(
    itemView: View
) : DynamicListSheetAdapter.Holder<SubstratePasswordAccountModel>(itemView) {

    override fun bind(
        item: SubstratePasswordAccountModel,
        isSelected: Boolean,
        handler: DynamicListSheetAdapter.Handler<SubstratePasswordAccountModel>
    ) {
        super.bind(item, isSelected, handler)

        with(itemView) {
            if (isSelected) {
                findViewById<ImageView>(R.id.substratePasswordAccountSelected).makeVisible()
            } else {
                findViewById<ImageView>(R.id.substratePasswordAccountSelected).makeInvisible()
            }
            findViewById<TextView>(R.id.substratePasswordAccountTitle).text = item.title
            findViewById<TextView>(R.id.substratePasswordAccountAddress).text = item.address
        }
    }
}

private object SubstratePasswordAccountCallback : DiffUtil.ItemCallback<SubstratePasswordAccountModel>() {
    override fun areItemsTheSame(oldItem: SubstratePasswordAccountModel, newItem: SubstratePasswordAccountModel): Boolean {
        return oldItem.rule == newItem.rule
    }

    override fun areContentsTheSame(oldItem: SubstratePasswordAccountModel, newItem: SubstratePasswordAccountModel): Boolean {
        return oldItem == newItem
    }
}
