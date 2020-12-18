package jp.co.soramitsu.feature_account_impl.presentation.node.list

import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.common.list.BaseGroupedDiffCallback
import jp.co.soramitsu.common.list.GroupedListAdapter
import jp.co.soramitsu.common.list.GroupedListHolder
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.node.model.NodeHeaderModel
import jp.co.soramitsu.feature_account_impl.presentation.node.model.NodeModel
import kotlinx.android.synthetic.main.item_node.view.nodeCheck
import kotlinx.android.synthetic.main.item_node.view.nodeDelete
import kotlinx.android.synthetic.main.item_node.view.nodeHost
import kotlinx.android.synthetic.main.item_node.view.nodeIcon
import kotlinx.android.synthetic.main.item_node.view.nodeInfo
import kotlinx.android.synthetic.main.item_node.view.nodeTitle
import kotlinx.android.synthetic.main.item_node_group.view.nodeGroupTitle

class NodesAdapter(
    private val nodeItemHandler: NodeItemHandler
) : GroupedListAdapter<NodeHeaderModel, NodeModel>(NodesDiffCallback) {

    interface NodeItemHandler {

        fun infoClicked(nodeModel: NodeModel, isChecked: Boolean)

        fun checkClicked(nodeModel: NodeModel)

        fun deleteClicked(nodeModel: NodeModel)
    }

    private var selectedItem: NodeModel? = null
    private var editMode = false

    fun updateSelectedNode(newSelection: NodeModel) {
        val positionToHide = selectedItem?.let { selected ->
            findIndexOfElement<NodeModel> { selected.id == it.id }
        }

        val positionToShow = findIndexOfElement<NodeModel> {
            newSelection.id == it.id
        }

        selectedItem = newSelection

        positionToHide?.let { notifyItemChanged(it) }
        notifyItemChanged(positionToShow)
    }

    fun switchToEdit(editable: Boolean) {
        editMode = editable

        val firstCustomNodeIndex = currentList.indexOfFirst { it is NodeModel && !it.isDefault }

        if (firstCustomNodeIndex == -1) return

        val customNodesCount = currentList.size - firstCustomNodeIndex
        notifyItemRangeChanged(firstCustomNodeIndex, customNodesCount)
    }

    override fun createGroupViewHolder(parent: ViewGroup): GroupedListHolder {
        return NodeGroupHolder(parent.inflateChild(R.layout.item_node_group))
    }

    override fun createChildViewHolder(parent: ViewGroup): GroupedListHolder {
        return NodeHolder(parent.inflateChild(R.layout.item_node))
    }

    override fun bindGroup(holder: GroupedListHolder, group: NodeHeaderModel) {
        (holder as NodeGroupHolder).bind(group)
    }

    override fun bindChild(holder: GroupedListHolder, child: NodeModel) {
        val isChecked = child.id == selectedItem?.id

        (holder as NodeHolder).bind(child, nodeItemHandler, isChecked, editMode)
    }
}

class NodeGroupHolder(view: View) : GroupedListHolder(view) {
    fun bind(nodeHeaderModel: NodeHeaderModel) = with(containerView) {
        nodeGroupTitle.text = nodeHeaderModel.title
    }
}

class NodeHolder(view: View) : GroupedListHolder(view) {

    fun bind(
        nodeModel: NodeModel,
        handler: NodesAdapter.NodeItemHandler,
        isChecked: Boolean,
        editMode: Boolean
    ) {
        with(containerView) {
            nodeTitle.text = nodeModel.name
            nodeHost.text = nodeModel.link

            nodeCheck.visibility = if (isChecked) View.VISIBLE else View.INVISIBLE

            if (!isChecked && !nodeModel.isDefault && editMode) {
                nodeDelete.visibility = View.VISIBLE
                nodeDelete.setOnClickListener { handler.deleteClicked(nodeModel) }
                nodeInfo.visibility = View.INVISIBLE
                nodeInfo.setOnClickListener(null)
                isEnabled = false
                setOnClickListener(null)
            } else {
                nodeDelete.visibility = View.GONE
                nodeDelete.setOnClickListener(null)
                nodeInfo.visibility = View.VISIBLE
                nodeInfo.setOnClickListener { handler.infoClicked(nodeModel, isChecked) }
                isEnabled = true
                setOnClickListener { handler.checkClicked(nodeModel) }
            }

            nodeIcon.setImageResource(nodeModel.networkModelType.icon)
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
        return oldItem.id == newItem.id
    }

    override fun areChildContentsTheSame(oldItem: NodeModel, newItem: NodeModel): Boolean {
        return oldItem == newItem
    }
}