package jp.co.soramitsu.feature_wallet_impl.presentation.balance.list

import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.ImageLoader
import coil.load
import jp.co.soramitsu.common.list.PayloadGenerator
import jp.co.soramitsu.common.list.resolvePayload
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.shape.addRipple
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.common.view.shape.getCutLeftBottomCornerDrawableFromColors
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.model.AssetModel
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_asset.view.chainAssetNameBadge
import kotlinx.android.synthetic.main.item_asset.view.itemAssetBalance
import kotlinx.android.synthetic.main.item_asset.view.itemAssetContainer
import kotlinx.android.synthetic.main.item_asset.view.itemAssetFiatAmount
import kotlinx.android.synthetic.main.item_asset.view.itemAssetImage
import kotlinx.android.synthetic.main.item_asset.view.itemAssetNetwork
import kotlinx.android.synthetic.main.item_asset.view.itemAssetRate
import kotlinx.android.synthetic.main.item_asset.view.itemAssetRateChange
import kotlinx.android.synthetic.main.item_asset.view.itemAssetToken
import kotlinx.android.synthetic.main.item_asset.view.networkBadge
import kotlinx.android.synthetic.main.item_asset.view.testnetBadge
import java.math.BigDecimal

val fiatRateExtractor = { assetModel: AssetModel -> assetModel.token.fiatRate }
val recentChangeExtractor = { assetModel: AssetModel -> assetModel.token.recentRateChange }

class BalanceListAdapter(
    private val imageLoader: ImageLoader,
    private val itemHandler: ItemAssetHandler,
) : ListAdapter<AssetModel, AssetViewHolder>(AssetDiffCallback) {

    interface ItemAssetHandler {
        fun assetClicked(asset: AssetModel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssetViewHolder {
        val view = parent.inflateChild(R.layout.item_asset)

        return AssetViewHolder(view, imageLoader)
    }

    override fun onBindViewHolder(holder: AssetViewHolder, position: Int) {
        val item = getItem(position)

        holder.bind(item, itemHandler)
    }

    override fun onBindViewHolder(holder: AssetViewHolder, position: Int, payloads: MutableList<Any>) {
        val item = getItem(position)

        resolvePayload(holder, position, payloads) {
            when (it) {
                fiatRateExtractor -> holder.bindFiatInfo(item)
                recentChangeExtractor -> holder.bindRecentChange(item)
                AssetModel::total -> holder.bindTotal(item)
            }
        }
    }
}

class AssetViewHolder(
    override val containerView: View,
    private val imageLoader: ImageLoader,
) : RecyclerView.ViewHolder(containerView), LayoutContainer {
    init {
        with(containerView) {
            val background = with(context) {
                addRipple(getCutCornerDrawable(R.color.blurColor))
            }

            containerView.itemAssetContainer.background = background
        }
    }

    fun bind(asset: AssetModel, itemHandler: BalanceListAdapter.ItemAssetHandler) = with(containerView) {
        itemAssetImage.load(asset.token.configuration.iconUrl, imageLoader)
        itemAssetNetwork.text = asset.token.configuration.name

        bindFiatInfo(asset)

        bindRecentChange(asset)

        bindTotal(asset)

        itemAssetToken.text = asset.token.configuration.symbol

        networkBadge.setVisible(!asset.token.configuration.isNative)
        networkBadge.setText(asset.token.configuration.chainName)
        networkBadge.setIcon(asset.token.configuration.chainIcon, imageLoader)

        testnetBadge.setVisible(asset.token.configuration.isTestNet == true)

        setOnClickListener { itemHandler.assetClicked(asset) }

        chainAssetNameBadge.isVisible = !asset.chainAccountName.isNullOrEmpty()
        chainAssetNameBadge.text = asset.chainAccountName
        chainAssetNameBadge.background = context.getCutLeftBottomCornerDrawableFromColors()
    }

    fun bindTotal(asset: AssetModel) {
        containerView.itemAssetBalance.text = asset.total.format()

        bindFiatAmount(asset.fiatAmount, asset.token.fiatSymbol)
    }

    fun bindRecentChange(asset: AssetModel) = with(containerView) {
        itemAssetRateChange.setTextColorRes(asset.token.rateChangeColorRes)
        itemAssetRateChange.text = asset.token.recentRateChange?.formatAsChange()
    }

    fun bindFiatInfo(asset: AssetModel) = with(containerView) {
        itemAssetRate.text = asset.token.fiatRate?.formatAsCurrency(asset.token.fiatSymbol)
        bindFiatAmount(asset.fiatAmount, asset.token.fiatSymbol)
    }

    private fun bindFiatAmount(fiatAmount: BigDecimal?, fiatSymbol: String?) {
        containerView.itemAssetFiatAmount.text = fiatAmount?.formatAsCurrency(fiatSymbol)
    }
}

private object AssetDiffCallback : DiffUtil.ItemCallback<AssetModel>() {

    override fun areItemsTheSame(oldItem: AssetModel, newItem: AssetModel): Boolean {
        return oldItem.token.configuration.chainToSymbol == newItem.token.configuration.chainToSymbol && oldItem.metaId in listOf(null, newItem.metaId)
    }

    override fun areContentsTheSame(oldItem: AssetModel, newItem: AssetModel): Boolean {
        return oldItem == newItem
    }

    override fun getChangePayload(oldItem: AssetModel, newItem: AssetModel): Any? {
        return AssetPayloadGenerator.diff(oldItem, newItem)
    }
}

private object AssetPayloadGenerator : PayloadGenerator<AssetModel>(
    fiatRateExtractor, recentChangeExtractor, AssetModel::total
)
