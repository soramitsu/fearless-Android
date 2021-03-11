package jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.DiffUtil
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.utils.makeInvisible
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.ClickHandler
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListSheetAdapter
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.HolderCreator
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model.NetworkModel
import kotlinx.android.synthetic.main.item_network.view.networkTv
import kotlinx.android.synthetic.main.item_network.view.rightIcon

class NetworkChooserBottomSheetDialog(
    context: Context,
    payload: Payload<NetworkModel>,
    onClicked: ClickHandler<NetworkModel>
) : DynamicListBottomSheet<NetworkModel>(context, payload, NetworkModelDiffCallback, onClicked) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.common_choose_network)
    }

    override fun holderCreator(): HolderCreator<NetworkModel> = {
        NodeViewHolder(it.inflateChild(R.layout.item_network))
    }
}

class NodeViewHolder(
    itemView: View
) : DynamicListSheetAdapter.Holder<NetworkModel>(itemView) {

    override fun bind(item: NetworkModel, isSelected: Boolean, handler: DynamicListSheetAdapter.Handler<NetworkModel>) {
        super.bind(item, isSelected, handler)

        with(itemView) {
            if (isSelected) {
                rightIcon.makeVisible()
            } else {
                rightIcon.makeInvisible()
            }

            networkTv.text = item.name
            networkTv.setCompoundDrawablesWithIntrinsicBounds(item.networkTypeUI.icon, 0, 0, 0)
        }
    }
}

private object NetworkModelDiffCallback : DiffUtil.ItemCallback<NetworkModel>() {
    override fun areItemsTheSame(oldItem: NetworkModel, newItem: NetworkModel): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: NetworkModel, newItem: NetworkModel): Boolean {
        return oldItem == newItem
    }
}