package jp.co.soramitsu.account.impl.presentation.node.list

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import jp.co.soramitsu.common.list.BaseGroupedDiffCallback
import jp.co.soramitsu.common.list.GroupedListAdapter
import jp.co.soramitsu.common.list.GroupedListHolder
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.account.impl.presentation.node.model.NodeHeaderModel
import jp.co.soramitsu.account.impl.presentation.node.model.NodeModel

class NodesAdapter(
    private val nodeItemHandler: NodeItemHandler
) : GroupedListAdapter<NodeHeaderModel, NodeModel>(NodesDiffCallback) {

    interface NodeItemHandler {

        fun infoClicked(nodeModel: NodeModel)

        fun checkClicked(nodeModel: NodeModel)

        fun deleteClicked(nodeModel: NodeModel)
    }

    private var editMode = false
    private var isAuto = true

    @SuppressLint("NotifyDataSetChanged")
    fun switchToEdit(editable: Boolean) {
        editMode = editable

        val firstCustomNodeIndex = currentList.indexOfFirst { it is NodeModel && !it.isDefault }

        if (firstCustomNodeIndex == -1) return

        val customNodesCount = currentList.size - firstCustomNodeIndex
        notifyItemRangeChanged(firstCustomNodeIndex, customNodesCount)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun handleAutoSelected(isAuto: Boolean) {
        this.isAuto = isAuto
        notifyDataSetChanged()
    }

    override fun createGroupViewHolder(parent: ViewGroup): GroupedListHolder {
        return NodeGroupHolder(parent.inflateChild(R.layout.item_node_group))
    }

    override fun createChildViewHolder(parent: ViewGroup): GroupedListHolder {
        return NodeHolder(parent.inflateChild(R.layout.item_node))
    }

    override fun bindGroup(holder: GroupedListHolder, group: NodeHeaderModel) {
        (holder as NodeGroupHolder).bind(group, isAuto)
    }

    override fun bindChild(holder: GroupedListHolder, child: NodeModel) {
        (holder as NodeHolder).bind(child, nodeItemHandler, { editMode }, isAuto)
    }
}

class NodeGroupHolder(view: View) : GroupedListHolder(view) {
    fun bind(nodeHeaderModel: NodeHeaderModel, isAuto: Boolean) = with(containerView) {
        findViewById<TextView>(R.id.nodeGroupTitle).text = nodeHeaderModel.title
        isEnabled = !isAuto
    }
}

class NodeHolder(view: View) : GroupedListHolder(view) {

    private val nodeTitle: TextView = view.findViewById(R.id.nodeTitle)
    private val nodeHost: TextView = view.findViewById(R.id.nodeHost)
    private val nodeCheck: ImageView = view.findViewById(R.id.nodeCheck)
    private val nodeDelete: ImageView = view.findViewById(R.id.nodeDelete)
    private val nodeInfo: ImageView = view.findViewById(R.id.nodeInfo)

    fun bind(
        nodeModel: NodeModel,
        handler: NodesAdapter.NodeItemHandler,
        getEditMode: () -> Boolean,
        isAuto: Boolean
    ) {
        with(containerView) {
            nodeTitle.isEnabled = !isAuto
            nodeTitle.text = nodeModel.name
            nodeHost.text = nodeModel.link

            val isChecked = nodeModel.isActive

            nodeCheck.visibility = if (isChecked && !isAuto) View.VISIBLE else View.INVISIBLE

            if (getEditMode() && !isChecked && !nodeModel.isDefault) {
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
                nodeInfo.setOnClickListener { handler.infoClicked(nodeModel) }
                isEnabled = true
                setOnClickListener {
                    when {
                        isAuto || getEditMode() -> Unit
                        else -> handler.checkClicked(nodeModel)
                    }
                }
            }
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
