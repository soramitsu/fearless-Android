package jp.co.soramitsu.feature_onboarding_impl.presentation.import_account.dialog

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
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.NetworkType
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_onboarding_impl.R

class NodeListAdapter(
    var selectedEncryptionType: Node,
    private val itemClickListener: (Node) -> Unit
) : ListAdapter<Node, NodeViewHolder>(DiffCallback3) {

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): NodeViewHolder {
        return NodeViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.item_chooser_list, viewGroup, false))
    }

    override fun onBindViewHolder(nodeViewHolder: NodeViewHolder, position: Int) {
        nodeViewHolder.bind(getItem(position), selectedEncryptionType, itemClickListener)
    }
}

class NodeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val networkTypeText: TextView = itemView.findViewById(R.id.chooserText)
    private val selectedPinIcon: ImageView = itemView.findViewById(R.id.rightIcon)

    fun bind(node: Node, selectedNode: Node, itemClickListener: (Node) -> Unit) {
        with(itemView) {
            if (node.link == selectedNode.link) {
                selectedPinIcon.makeVisible()
            } else {
                selectedPinIcon.makeInvisible()
            }

            networkTypeText.text = node.name



            setOnClickListener {
                itemClickListener(node)
            }
        }
    }
}

object DiffCallback3 : DiffUtil.ItemCallback<Node>() {
    override fun areItemsTheSame(oldItem: Node, newItem: Node): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: Node, newItem: Node): Boolean {
        return oldItem == newItem
    }
}