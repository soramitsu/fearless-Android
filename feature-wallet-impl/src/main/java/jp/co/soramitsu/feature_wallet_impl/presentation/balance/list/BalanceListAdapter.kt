package jp.co.soramitsu.feature_wallet_impl.presentation.balance.list

import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
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
import jp.co.soramitsu.feature_wallet_impl.presentation.model.AssetWithStateModel
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
import kotlinx.android.synthetic.main.item_asset_shimmer.view.itemAssetBalanceShimmer
import kotlinx.android.synthetic.main.item_asset_shimmer.view.itemAssetFiatAmountShimmer
import kotlinx.android.synthetic.main.item_asset_shimmer.view.itemAssetRateShimmer
import java.math.BigDecimal

val fiatRateExtractor = { item: AssetWithStateModel -> item.asset.token.fiatRate }
val recentChangeExtractor = { item: AssetWithStateModel -> item.asset.token.recentRateChange }
val totalExtractor = { item: AssetWithStateModel -> item.asset.total }
val stateExtractor = { item: AssetWithStateModel -> item.state }

class BalanceListAdapter(
    private val imageLoader: ImageLoader,
    private val itemHandler: ItemAssetHandler,
) : ListAdapter<AssetWithStateModel, AssetViewHolder>(AssetDiffCallback) {

    interface ItemAssetHandler {
        fun assetClicked(asset: AssetModel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssetViewHolder {
        val view = parent.inflateChild(R.layout.item_asset_with_shimmer)

        return AssetViewHolder(view, imageLoader)
    }

    override fun onBindViewHolder(holder: AssetViewHolder, position: Int) {
        val item = getItem(position)

        holder.bind(item, itemHandler)
    }

    override fun onBindViewHolder(holder: AssetViewHolder, position: Int, payloads: MutableList<Any>) {
        val model = getItem(position)

        resolvePayload(holder, position, payloads) {
            when (it) {
                stateExtractor -> holder.bindState(model)
                fiatRateExtractor -> holder.bindFiatInfo(model)
                recentChangeExtractor -> holder.bindRecentChange(model)
                totalExtractor -> holder.bindTotal(model)
            }
        }
    }
}

class AssetViewHolder(
    override val containerView: View,
    private val imageLoader: ImageLoader,
) : RecyclerView.ViewHolder(containerView), LayoutContainer {
    private val shimmer = containerView.findViewById<ConstraintLayout>(R.id.item_asset_shimmer)
    private val content = containerView.findViewById<ConstraintLayout>(R.id.item_asset_content)

    init {
        with(containerView) {
            val background = with(context) {
                addRipple(getCutCornerDrawable(R.color.blurColor))
            }

            containerView.itemAssetContainer.background = background
        }
    }

    fun bind(model: AssetWithStateModel, itemHandler: BalanceListAdapter.ItemAssetHandler) {
        val asset = model.asset

        content.itemAssetImage.load(asset.token.configuration.iconUrl, imageLoader)
        shimmer.itemAssetImage.load(asset.token.configuration.iconUrl, imageLoader)

        content.itemAssetNetwork.text = asset.token.configuration.name
        shimmer.itemAssetNetwork.text = asset.token.configuration.name

        content.itemAssetToken.text = asset.token.configuration.symbol
        shimmer.itemAssetToken.text = asset.token.configuration.symbol

        content.networkBadge.setText(asset.token.configuration.chainName)
        content.networkBadge.setIcon(asset.token.configuration.chainIcon, imageLoader)
        shimmer.networkBadge.setText(asset.token.configuration.chainName)
        shimmer.networkBadge.setIcon(asset.token.configuration.chainIcon, imageLoader)

        content.setOnClickListener { itemHandler.assetClicked(asset) }
        shimmer.setOnClickListener { itemHandler.assetClicked(asset) }

        content.chainAssetNameBadge.text = asset.chainAccountName
        content.chainAssetNameBadge.background = content.context.getCutLeftBottomCornerDrawableFromColors()
        shimmer.chainAssetNameBadge.text = asset.chainAccountName
        shimmer.chainAssetNameBadge.background = content.context.getCutLeftBottomCornerDrawableFromColors()

        bindState(model)
    }

    fun bindState(model: AssetWithStateModel) {
        val asset = model.asset
        val state = model.state

        content.itemAssetImage.setVisible(state.chainUpdate == false, View.INVISIBLE)
        shimmer.itemAssetImage.setVisible(state.chainUpdate != false, View.INVISIBLE)

        content.itemAssetNetwork.setVisible(state.chainUpdate == false, View.INVISIBLE)
        shimmer.itemAssetNetwork.setVisible(state.chainUpdate != false, View.INVISIBLE)

        content.itemAssetToken.setVisible(state.chainUpdate == false, View.INVISIBLE)
        shimmer.itemAssetToken.setVisible(state.chainUpdate != false, View.INVISIBLE)

        content.networkBadge.isVisible = !asset.token.configuration.isNative && state.chainUpdate == false
        shimmer.networkBadge.isVisible = !asset.token.configuration.isNative && state.chainUpdate != false

        content.testnetBadge.isVisible = asset.token.configuration.isTestNet == true && state.chainUpdate == false
        shimmer.testnetBadge.isVisible = asset.token.configuration.isTestNet == true && state.chainUpdate != false

        content.chainAssetNameBadge.isVisible = !asset.chainAccountName.isNullOrEmpty() && state.chainUpdate == false
        shimmer.chainAssetNameBadge.isVisible = !asset.chainAccountName.isNullOrEmpty() && state.chainUpdate != false

        bindFiatInfo(model)

        bindRecentChange(model)

        bindTotal(model)
    }

    fun bindTotal(model: AssetWithStateModel) {
        val asset = model.asset
        shimmer.itemAssetBalanceShimmer.isVisible = asset.total == null && model.state.isBalanceUpdating
        shimmer.itemAssetBalance.setVisible(asset.total != null && model.state.isBalanceUpdating, View.INVISIBLE)
        shimmer.itemAssetBalance.text = asset.total?.format()
        content.itemAssetBalance.text = asset.total?.format()
        content.itemAssetBalance.setVisible(!model.state.isBalanceUpdating, View.INVISIBLE)

        bindFiatAmount(asset.fiatAmount, asset.token.fiatSymbol, model.state.isFiatUpdating)
    }

    fun bindRecentChange(model: AssetWithStateModel) {
        val asset = model.asset
        shimmer.itemAssetRateChange.setVisible(
            asset.token.fiatRate != null && asset.token.recentRateChange != null && model.state.isRateUpdating,
            View.INVISIBLE
        )
        shimmer.itemAssetRateChange.setTextColorRes(R.color.white_64)
        shimmer.itemAssetRateChange.text = asset.token.recentRateChange?.formatAsChange()
        content.itemAssetRateChange.setTextColorRes(asset.token.rateChangeColorRes)
        content.itemAssetRateChange.text = asset.token.recentRateChange?.formatAsChange()
        content.itemAssetRateChange.setVisible(!model.state.isRateUpdating, View.INVISIBLE)
    }

    fun bindFiatInfo(model: AssetWithStateModel) {
        val asset = model.asset
        shimmer.itemAssetRateShimmer.isVisible = asset.token.fiatRate == null && model.state.isRateUpdating
        shimmer.itemAssetRate.setVisible(asset.token.fiatRate != null && model.state.isRateUpdating, View.INVISIBLE)

        shimmer.itemAssetRate.text = asset.token.fiatRate?.formatAsCurrency(asset.token.fiatSymbol)
        content.itemAssetRate.text = asset.token.fiatRate?.formatAsCurrency(asset.token.fiatSymbol)
        content.itemAssetRate.setVisible(asset.token.fiatRate != null && !model.state.isRateUpdating, View.INVISIBLE)
        bindFiatAmount(asset.fiatAmount, asset.token.fiatSymbol, model.state.isFiatUpdating)
    }

    private fun bindFiatAmount(fiatAmount: BigDecimal?, fiatSymbol: String?, isUpdating: Boolean) {
        shimmer.itemAssetFiatAmountShimmer.isVisible = fiatAmount == null && isUpdating
        shimmer.itemAssetFiatAmount.setVisible(fiatAmount != null && isUpdating, View.INVISIBLE)
        shimmer.itemAssetFiatAmount.text = fiatAmount?.formatAsCurrency(fiatSymbol)
        content.itemAssetFiatAmount.text = fiatAmount?.formatAsCurrency(fiatSymbol)
        content.itemAssetFiatAmount.setVisible(!isUpdating, View.INVISIBLE)
    }
}

private object AssetDiffCallback : DiffUtil.ItemCallback<AssetWithStateModel>() {
    override fun areItemsTheSame(oldItem: AssetWithStateModel, newItem: AssetWithStateModel): Boolean {
        return oldItem.asset.token.configuration.chainToSymbol == newItem.asset.token.configuration.chainToSymbol &&
            oldItem.asset.metaId in listOf(null, newItem.asset.metaId)
    }

    override fun areContentsTheSame(oldItem: AssetWithStateModel, newItem: AssetWithStateModel): Boolean {
        return oldItem == newItem
    }

    override fun getChangePayload(oldItem: AssetWithStateModel, newItem: AssetWithStateModel): Any? {
        return AssetPayloadGenerator.diff(oldItem, newItem)
    }
}

private object AssetPayloadGenerator : PayloadGenerator<AssetWithStateModel>(
    fiatRateExtractor, recentChangeExtractor, totalExtractor, stateExtractor
)
