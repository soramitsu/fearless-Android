package jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.utils.makeInvisible
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model.NetworkModel
import kotlinx.android.synthetic.main.item_network.view.networkTv
import kotlinx.android.synthetic.main.item_network.view.rightIcon

class NetworkAdapter(
    private val itemClickListener: (NetworkModel) -> Unit
) : ListAdapter<NetworkModel, NodeViewHolder>(DiffCallback3) {

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): NodeViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.item_network, viewGroup, false)
        return NodeViewHolder(view)
    }

    override fun onBindViewHolder(nodeViewHolder: NodeViewHolder, position: Int) {
        nodeViewHolder.bind(getItem(position), itemClickListener)
    }
}

class NodeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    fun bind(node: NetworkModel, itemClickListener: (NetworkModel) -> Unit) {
        with(itemView) {
            if (node.isSelected) {
                rightIcon.makeVisible()
            } else {
                rightIcon.makeInvisible()
            }

            networkTv.text = node.name
            networkTv.setCompoundDrawablesWithIntrinsicBounds(node.icon, 0, 0, 0)

            setOnClickListener {
                itemClickListener(node)
            }
        }
    }
}

object DiffCallback3 : DiffUtil.ItemCallback<NetworkModel>() {
    override fun areItemsTheSame(oldItem: NetworkModel, newItem: NetworkModel): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: NetworkModel, newItem: NetworkModel): Boolean {
        return oldItem == newItem
    }
}