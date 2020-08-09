package jp.co.soramitsu.feature_onboarding_impl.presentation.importing.network

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.utils.makeInvisible
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.presentation.importing.network.model.NodeModel

class NodeListAdapter(
    private val itemClickListener: (NodeModel) -> Unit
) : ListAdapter<NodeModel, NodeViewHolder>(DiffCallback3) {

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): NodeViewHolder {
        return NodeViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.item_chooser_list, viewGroup, false))
    }

    override fun onBindViewHolder(nodeViewHolder: NodeViewHolder, position: Int) {
        nodeViewHolder.bind(getItem(position), itemClickListener)
    }
}

class NodeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val networkTypeText: TextView = itemView.findViewById(R.id.chooserText)
    private val selectedPinIcon: ImageView = itemView.findViewById(R.id.rightIcon)

    fun bind(node: NodeModel, itemClickListener: (NodeModel) -> Unit) {
        with(itemView) {
            if (node.isSelected) {
                selectedPinIcon.makeVisible()
            } else {
                selectedPinIcon.makeInvisible()
            }

            networkTypeText.text = node.name
            networkTypeText.setCompoundDrawablesWithIntrinsicBounds(node.icon, 0, 0, 0)

            setOnClickListener {
                itemClickListener(node)
            }
        }
    }
}

object DiffCallback3 : DiffUtil.ItemCallback<NodeModel>() {
    override fun areItemsTheSame(oldItem: NodeModel, newItem: NodeModel): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: NodeModel, newItem: NodeModel): Boolean {
        return oldItem == newItem
    }
}