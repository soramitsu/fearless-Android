package jp.co.soramitsu.feature_wallet_impl.presentation.balance.list

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.list.PayloadGenerator
import jp.co.soramitsu.common.list.resolvePayload
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.common.view.shape.addRipple
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

    override fun onBindViewHolder(holder: AssetViewHolder, position: Int, payloads: MutableList<Any>) {
        val item = getItem(position)

        resolvePayload(holder, position, payloads) {
            when (it) {
                AssetModel::dollarRate -> holder.bindDollarInfo(item)
                AssetModel::recentRateChange -> holder.bindRecentChange(item)
                AssetModel::total -> holder.bindTotal(item)
            }
        }
    }
}

class AssetViewHolder(override val containerView: View) : RecyclerView.ViewHolder(containerView), LayoutContainer {
    init {
        with(containerView) {
            val background = with(context) {
                addRipple(getCutCornerDrawable(R.color.blurColor))
            }

            containerView.itemAssetContainer.background = background
        }
    }

    fun bind(asset: AssetModel, itemHandler: BalanceListAdapter.ItemAssetHandler) = with(containerView) {
        itemAssetImage.setImageResource(asset.token.icon)
        itemAssetNetwork.text = asset.token.networkType.readableName

        bindDollarInfo(asset)

        bindRecentChange(asset)

        bindTotal(asset)

        itemAssetToken.text = asset.token.displayName

        setOnClickListener { itemHandler.assetClicked(asset) }
    }

    fun bindTotal(asset: AssetModel) {
        containerView.itemAssetBalance.text = asset.total.format()

        bindDollarAmount(asset.dollarAmount)
    }

    fun bindRecentChange(asset: AssetModel) = with(containerView) {
        asset.recentRateChange?.let {
            itemAssetRateChange.setTextColorRes(asset.rateChangeColorRes!!)
            itemAssetRateChange.text = it.formatAsChange()
        }
    }

    fun bindDollarInfo(asset: AssetModel) = with(containerView) {
        asset.dollarRate?.let { itemAssetRate.text = it.formatAsCurrency() }
        bindDollarAmount(asset.dollarAmount)
    }

    private fun bindDollarAmount(dollarAmount: BigDecimal?) {
        dollarAmount?.let { containerView.itemAssetDollarAmount.text = it.formatAsCurrency() }
    }
}

private object AssetDiffCallback : DiffUtil.ItemCallback<AssetModel>() {

    override fun areItemsTheSame(oldItem: AssetModel, newItem: AssetModel): Boolean {
        return oldItem.token == newItem.token
    }

    override fun areContentsTheSame(oldItem: AssetModel, newItem: AssetModel): Boolean {
        return oldItem == newItem
    }

    override fun getChangePayload(oldItem: AssetModel, newItem: AssetModel): Any? {
        return AssetPayloadGenerator.diff(oldItem, newItem)
    }
}

private object AssetPayloadGenerator : PayloadGenerator<AssetModel>(
    AssetModel::dollarRate, AssetModel::recentRateChange, AssetModel::total
)
