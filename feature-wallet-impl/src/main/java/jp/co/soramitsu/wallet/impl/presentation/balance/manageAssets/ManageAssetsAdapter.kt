package jp.co.soramitsu.wallet.impl.presentation.balance.manageAssets

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.ImageLoader
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

class ManageAssetsAdapter(
    private val handler: Handler,
    private val imageLoader: ImageLoader
) :
    ListAdapter<ManageAssetModel, ManageAssetViewHolder>(diffUtil), ManageAssetViewHolder.Listener {

    interface Handler {
        fun switch(item: ManageAssetModel)
        fun addAccount(chainId: ChainId, chainName: String, symbol: String, markedAsNotNeed: Boolean)
        fun startDrag(viewHolder: RecyclerView.ViewHolder)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ManageAssetViewHolder =
        ManageAssetViewHolder(
            parent.inflateChild(R.layout.item_manage_asset),
            imageLoader,
            this@ManageAssetsAdapter
        )

    override fun onBindViewHolder(holder: ManageAssetViewHolder, position: Int) =
        holder.bind(getItem(position))

    override fun switch(itemPosition: Int, checked: Boolean) {
        val item = getItem(itemPosition)
        if (item.enabled == checked) return
        handler.switch(item)
    }

    override fun addAccount(chainId: ChainId, chainName: String, symbol: String, markedAsNotNeed: Boolean) =
        handler.addAccount(chainId, chainName, symbol, markedAsNotNeed)

    override fun startDrag(viewHolder: RecyclerView.ViewHolder) {
        handler.startDrag(viewHolder)
    }
}

private val diffUtil = object : DiffUtil.ItemCallback<ManageAssetModel>() {
    override fun areItemsTheSame(oldItem: ManageAssetModel, newItem: ManageAssetModel) =
        oldItem.chainId == newItem.chainId && oldItem.tokenSymbol == newItem.tokenSymbol

    override fun areContentsTheSame(oldItem: ManageAssetModel, newItem: ManageAssetModel) =
        oldItem == newItem
}
