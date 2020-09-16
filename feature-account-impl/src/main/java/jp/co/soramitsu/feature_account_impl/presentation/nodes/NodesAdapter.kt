package jp.co.soramitsu.feature_account_impl.presentation.nodes

import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.common.groupedList.BaseGroupedDiffCallback
import jp.co.soramitsu.feature_account_impl.presentation.common.groupedList.GroupedListAdapter
import jp.co.soramitsu.feature_account_impl.presentation.common.groupedList.GroupedListHolder
import jp.co.soramitsu.feature_account_impl.presentation.nodes.model.NodeHeaderModel
import jp.co.soramitsu.feature_account_impl.presentation.nodes.model.NodeModel
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model.NetworkModel

class NodesAdapter(
    private val networkItemHandler: NetworkItemHandler
) : GroupedListAdapter<NodeHeaderModel, NodeModel>(NodesDiffCallback) {

    interface NetworkItemHandler {

        fun infoClicked(networkModel: NetworkModel)

        fun checkClicked(networkModel: NetworkModel)
    }

    private var selectedItem: NodeModel? = null

    fun updateSelectedNode(newSelection: NodeModel) {
        val positionToHide = selectedItem?.let { selected ->
            findIndexOfElement<NodeModel> { selected.link == it.link }
        }

        val positionToShow = findIndexOfElement<NodeModel> {
            newSelection.link == it.link
        }

        selectedItem = newSelection

        positionToHide?.let { notifyItemChanged(it) }
        notifyItemChanged(positionToShow)
    }

    override fun createGroupViewHolder(parent: ViewGroup): GroupedListHolder {
        return NodeGroupHolder(inflate(parent, R.layout.item_node_group))
    }

    override fun createChildViewHolder(parent: ViewGroup): GroupedListHolder {
        return NodeHolder(inflate(parent, R.layout.item_node))
    }

    override fun bindGroup(holder: GroupedListHolder, group: NodeHeaderModel) {
        (holder as NodeGroupHolder).bind(group)
    }

    override fun bindChild(holder: GroupedListHolder, child: NodeModel) {
        val isChecked = child.link == selectedItem?.link

        (holder as NodeHolder).bind(child, networkItemHandler, isChecked)
    }
}

class NodeGroupHolder(view: View) : GroupedListHolder(view) {
    fun bind(nodeHeaderModel: NodeHeaderModel) = with(containerView) {

    }
}

class NodeHolder(view: View) : GroupedListHolder(view) {

    fun bind(
        nodeModel: NodeModel,
        handler: NodesAdapter.NetworkItemHandler,
        isChecked: Boolean
    ) {
        with(containerView) {
        }
    }
}

private object NodesDiffCallback : BaseGroupedDiffCallback<NodeHeaderModel, NodeModel>(NodeHeaderModel::class.java) {

    override fun areGroupItemsTheSame(oldItem: NodeHeaderModel, newItem: NodeHeaderModel): Boolean {
        return oldItem.title == newItem.title
    }

    override fun areGroupContentsTheSame(oldItem: NodeHeaderModel, newItem: NodeHeaderModel): Boolean {
        return oldItem == newItem
    }

    override fun areChildItemsTheSame(oldItem: NodeModel, newItem: NodeModel): Boolean {
        return oldItem.link == newItem.link
    }

    override fun areChildContentsTheSame(oldItem: NodeModel, newItem: NodeModel): Boolean {
        return oldItem == newItem
    }
}