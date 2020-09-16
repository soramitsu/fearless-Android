package jp.co.soramitsu.feature_account_impl.presentation.networks

import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.common.groupedList.BaseGroupedDiffCallback
import jp.co.soramitsu.feature_account_impl.presentation.common.groupedList.GroupedListAdapter
import jp.co.soramitsu.feature_account_impl.presentation.common.groupedList.GroupedListHolder
import jp.co.soramitsu.feature_account_impl.presentation.networks.model.NetworkHeaderModel
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model.NetworkModel

class NetworksAdapter(
    private val networkItemHandler: NetworkItemHandler
) : GroupedListAdapter<NetworkHeaderModel, NetworkModel>(ConnectionsDiffCallback) {

    interface NetworkItemHandler {

        fun infoClicked(networkModel: NetworkModel)

        fun checkClicked(networkModel: NetworkModel)
    }

    private var selectedItem: NetworkModel? = null

    fun updateSelectedNetwork(newSelection: NetworkModel) {
        val positionToHide = selectedItem?.let { selected ->
            findIndexOfElement<NetworkModel> { selected.defaultNode.link == it.defaultNode.link }
        }

        val positionToShow = findIndexOfElement<NetworkModel> {
            newSelection.defaultNode.link == it.defaultNode.link
        }

        selectedItem = newSelection

        positionToHide?.let { notifyItemChanged(it) }
        notifyItemChanged(positionToShow)
    }

    override fun createGroupViewHolder(parent: ViewGroup): GroupedListHolder {
        return NetworkGroupHolder(inflate(parent, R.layout.item_network_group))
    }

    override fun createChildViewHolder(parent: ViewGroup): GroupedListHolder {
        return NetworkHolder(inflate(parent, R.layout.item_connection))
    }

    override fun bindGroup(holder: GroupedListHolder, group: NetworkHeaderModel) {
        (holder as NetworkGroupHolder).bind(group)
    }

    override fun bindChild(holder: GroupedListHolder, child: NetworkModel) {
        val isChecked = child.defaultNode.link == selectedItem?.defaultNode?.link

        (holder as NetworkHolder).bind(child, networkItemHandler, isChecked)
    }
}

class NetworkGroupHolder(view: View) : GroupedListHolder(view) {
    fun bind(networkHeaderModel: NetworkHeaderModel) = with(containerView) {

    }
}

class NetworkHolder(view: View) : GroupedListHolder(view) {

    fun bind(
        connectionModel: NetworkModel,
        handler: NetworksAdapter.NetworkItemHandler,
        isChecked: Boolean
    ) {
        with(containerView) {
        }
    }
}

private object ConnectionsDiffCallback : BaseGroupedDiffCallback<NetworkHeaderModel, NetworkModel>(NetworkHeaderModel::class.java) {

    override fun areGroupItemsTheSame(oldItem: NetworkHeaderModel, newItem: NetworkHeaderModel): Boolean {
        return oldItem.title == newItem.title
    }

    override fun areGroupContentsTheSame(oldItem: NetworkHeaderModel, newItem: NetworkHeaderModel): Boolean {
        return oldItem == newItem
    }

    override fun areChildItemsTheSame(oldItem: NetworkModel, newItem: NetworkModel): Boolean {
        return oldItem.defaultNode.link == newItem.defaultNode.link
    }

    override fun areChildContentsTheSame(oldItem: NetworkModel, newItem: NetworkModel): Boolean {
        return oldItem == newItem
    }
}