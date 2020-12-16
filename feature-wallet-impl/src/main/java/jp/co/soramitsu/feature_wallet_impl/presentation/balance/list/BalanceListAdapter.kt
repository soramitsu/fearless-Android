package jp.co.soramitsu.feature_wallet_impl.presentation.balance.list

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.model.AssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.model.icon
import jp.co.soramitsu.feature_wallet_impl.util.format
import jp.co.soramitsu.feature_wallet_impl.util.formatAsChange
import jp.co.soramitsu.feature_wallet_impl.util.formatAsCurrency
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_asset.view.itemAssetBalance
import kotlinx.android.synthetic.main.item_asset.view.itemAssetContainer
import kotlinx.android.synthetic.main.item_asset.view.itemAssetDollarAmount
import kotlinx.android.synthetic.main.item_asset.view.itemAssetImage
import kotlinx.android.synthetic.main.item_asset.view.itemAssetNetwork
import kotlinx.android.synthetic.main.item_asset.view.itemAssetRate
import kotlinx.android.synthetic.main.item_asset.view.itemAssetRateChange
import kotlinx.android.synthetic.main.item_asset.view.itemAssetToken
import java.math.BigDecimal

class BalanceListAdapter(private val itemHandler: ItemAssetHandler) : ListAdapter<AssetModel, AssetViewHolder>(AssetDiffCallback) {

    interface ItemAssetHandler {
        fun assetClicked(asset: AssetModel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssetViewHolder {
        val view = parent.inflateChild(R.layout.item_asset)

        return AssetViewHolder(view)
    }

    override fun onBindViewHolder(holder: AssetViewHolder, position: Int) {
        val item = getItem(position)

        holder.bind(item, itemHandler)
    }
}

class AssetViewHolder(override val containerView: View) : RecyclerView.ViewHolder(containerView), LayoutContainer {
    init {
        with(containerView) {
            containerView.itemAssetContainer.background = context.getCutCornerDrawable(R.color.blurColor)
        }
    }

    fun bind(asset: AssetModel, itemHandler: BalanceListAdapter.ItemAssetHandler) = with(containerView) {
        itemAssetImage.setImageResource(asset.token.icon)
        itemAssetNetwork.text = asset.token.networkType.readableName

        asset.dollarRate?.let { itemAssetRate.text = it.formatAsCurrency() }
        asset.recentRateChange?.let { showRateChange(it, asset.rateChangeColorRes!!) }
        asset.dollarAmount?.let { itemAssetDollarAmount.text = it.formatAsCurrency() }

        itemAssetBalance.text = asset.total.format()

        itemAssetToken.text = asset.token.displayName

        setOnClickListener { itemHandler.assetClicked(asset) }
    }

    private fun showRateChange(rateChange: BigDecimal, rateChangeColorRes: Int) = with(containerView) {
        itemAssetRateChange.setTextColorRes(rateChangeColorRes)
        itemAssetRateChange.text = rateChange.formatAsChange()
    }
}

private object AssetDiffCallback : DiffUtil.ItemCallback<AssetModel>() {
    override fun areItemsTheSame(oldItem: AssetModel, newItem: AssetModel): Boolean {
        return oldItem.token == newItem.token
    }

    override fun areContentsTheSame(oldItem: AssetModel, newItem: AssetModel): Boolean {
        return oldItem == newItem
    }
}