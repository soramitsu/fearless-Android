package jp.co.soramitsu.feature_wallet_impl.presentation.balance.list

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
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
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.BadgeView
import jp.co.soramitsu.common.view.shape.addRipple
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.common.view.shape.getCutLeftBottomCornerDrawableFromColors
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.model.AssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.model.AssetWithStateModel
import kotlinx.android.extensions.LayoutContainer
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
            if (it == stateExtractor) holder.bindState(model)
            if (it == fiatRateExtractor) holder.bindFiatInfo(model)
            if (it == recentChangeExtractor) holder.bindRecentChange(model)
            if (it == totalExtractor) holder.bindTotal(model)
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

            containerView.findViewById<ConstraintLayout>(R.id.itemAssetContainer).background = background
        }
    }

    fun bind(model: AssetWithStateModel, itemHandler: BalanceListAdapter.ItemAssetHandler) {
        val asset = model.asset

        content.alpha = when {
            asset.isSupported -> 1.0F
            else -> 0.4f
        }

        content.findViewById<ImageView>(R.id.itemAssetImage).load(asset.token.configuration.iconUrl, imageLoader)
        shimmer.findViewById<ImageView>(R.id.itemAssetImage).load(asset.token.configuration.iconUrl, imageLoader)

        content.findViewById<TextView>(R.id.itemAssetNetwork).text = asset.token.configuration.name
        shimmer.findViewById<TextView>(R.id.itemAssetNetwork).text = asset.token.configuration.name

        content.findViewById<TextView>(R.id.itemAssetToken).text = asset.token.configuration.symbol
        shimmer.findViewById<TextView>(R.id.itemAssetToken).text = asset.token.configuration.symbol

        content.findViewById<BadgeView>(R.id.networkBadge).setText(asset.token.configuration.chainName)
        content.findViewById<BadgeView>(R.id.networkBadge).setIcon(asset.token.configuration.chainIcon, imageLoader)
        shimmer.findViewById<BadgeView>(R.id.networkBadge).setText(asset.token.configuration.chainName)
        shimmer.findViewById<BadgeView>(R.id.networkBadge).setIcon(asset.token.configuration.chainIcon, imageLoader)

        content.setOnClickListener { itemHandler.assetClicked(asset) }
        shimmer.setOnClickListener { itemHandler.assetClicked(asset) }

        content.findViewById<TextView>(R.id.chainAssetNameBadge).text = asset.chainAccountName
        content.findViewById<TextView>(R.id.chainAssetNameBadge).background = content.context.getCutLeftBottomCornerDrawableFromColors()

        shimmer.findViewById<TextView>(R.id.chainAssetNameBadge).text = asset.chainAccountName
        shimmer.findViewById<TextView>(R.id.chainAssetNameBadge).background = content.context.getCutLeftBottomCornerDrawableFromColors()

        bindState(model)
    }

    fun bindState(model: AssetWithStateModel) {
        val asset = model.asset
        val state = model.state

        content.findViewById<TextView>(R.id.itemAssetImage).setVisible(state.chainUpdate == false, View.INVISIBLE)
        shimmer.findViewById<TextView>(R.id.itemAssetImage).setVisible(state.chainUpdate != false, View.INVISIBLE)

        content.findViewById<TextView>(R.id.itemAssetNetwork).setVisible(state.chainUpdate == false, View.INVISIBLE)
        shimmer.findViewById<TextView>(R.id.itemAssetNetwork).setVisible(state.chainUpdate != false, View.INVISIBLE)

        content.findViewById<TextView>(R.id.itemAssetToken).setVisible(state.chainUpdate == false, View.INVISIBLE)
        shimmer.findViewById<TextView>(R.id.itemAssetToken).setVisible(state.chainUpdate != false, View.INVISIBLE)

        content.findViewById<BadgeView>(R.id.networkBadge).isVisible = !asset.token.configuration.isNative && state.chainUpdate == false
        shimmer.findViewById<BadgeView>(R.id.networkBadge).isVisible = !asset.token.configuration.isNative && state.chainUpdate != false

        content.findViewById<BadgeView>(R.id.testnetBadge).isVisible = asset.token.configuration.isTestNet == true && state.chainUpdate == false
        shimmer.findViewById<BadgeView>(R.id.testnetBadge).isVisible = asset.token.configuration.isTestNet == true && state.chainUpdate != false

        content.findViewById<TextView>(R.id.chainAssetNameBadge).isVisible = !asset.chainAccountName.isNullOrEmpty() && state.chainUpdate == false
        shimmer.findViewById<TextView>(R.id.chainAssetNameBadge).isVisible = !asset.chainAccountName.isNullOrEmpty() && state.chainUpdate != false

        bindFiatInfo(model)

        bindRecentChange(model)

        bindTotal(model)
    }

    fun bindTotal(model: AssetWithStateModel) {
        val asset = model.asset
        shimmer.findViewById<View>(R.id.itemAssetBalanceShimmer).isVisible = asset.total == null && model.state.isBalanceUpdating
        shimmer.findViewById<TextView>(R.id.itemAssetBalance).setVisible(asset.total != null && model.state.isBalanceUpdating, View.INVISIBLE)
        shimmer.findViewById<TextView>(R.id.itemAssetBalance).text = asset.total.orZero().format()
        content.findViewById<TextView>(R.id.itemAssetBalance).text = asset.total.orZero().format()
        content.findViewById<TextView>(R.id.itemAssetBalance).setVisible(!model.state.isBalanceUpdating, View.INVISIBLE)

        bindFiatAmount(asset.fiatAmount, asset.token.fiatSymbol, model.state.isFiatUpdating)
    }

    fun bindRecentChange(model: AssetWithStateModel) {
        val asset = model.asset
        shimmer.findViewById<TextView>(R.id.itemAssetRateChange).setVisible(
            asset.token.fiatRate != null && asset.token.recentRateChange != null && model.state.isRateUpdating,
            View.INVISIBLE
        )
        shimmer.findViewById<TextView>(R.id.itemAssetRateChange).setTextColorRes(R.color.white_64)
        shimmer.findViewById<TextView>(R.id.itemAssetRateChange).text = asset.token.recentRateChange?.formatAsChange()
        content.findViewById<TextView>(R.id.itemAssetRateChange).setTextColorRes(asset.token.rateChangeColorRes)
        content.findViewById<TextView>(R.id.itemAssetRateChange).text = asset.token.recentRateChange?.formatAsChange()
        content.findViewById<TextView>(R.id.itemAssetRateChange).setVisible(!model.state.isRateUpdating, View.INVISIBLE)
    }

    fun bindFiatInfo(model: AssetWithStateModel) {
        val asset = model.asset
        shimmer.findViewById<View>(R.id.itemAssetRateShimmer).isVisible = asset.token.fiatRate == null && model.state.isRateUpdating
        shimmer.findViewById<TextView>(R.id.itemAssetRate).setVisible(asset.token.fiatRate != null && model.state.isRateUpdating, View.INVISIBLE)

        shimmer.findViewById<TextView>(R.id.itemAssetRate).text = asset.token.fiatRate?.formatAsCurrency(asset.token.fiatSymbol)
        content.findViewById<TextView>(R.id.itemAssetRate).text = asset.token.fiatRate?.formatAsCurrency(asset.token.fiatSymbol)
        content.findViewById<TextView>(R.id.itemAssetRate).setVisible(asset.token.fiatRate != null && !model.state.isRateUpdating, View.INVISIBLE)
        bindFiatAmount(asset.fiatAmount, asset.token.fiatSymbol, model.state.isFiatUpdating)
    }

    private fun bindFiatAmount(fiatAmount: BigDecimal?, fiatSymbol: String?, isUpdating: Boolean) {
        shimmer.findViewById<View>(R.id.itemAssetFiatAmountShimmer).isVisible = fiatAmount == null && isUpdating
        shimmer.findViewById<TextView>(R.id.itemAssetFiatAmount).setVisible(fiatAmount != null && isUpdating, View.INVISIBLE)
        shimmer.findViewById<TextView>(R.id.itemAssetFiatAmount).text = fiatAmount?.formatAsCurrency(fiatSymbol)
        content.findViewById<TextView>(R.id.itemAssetFiatAmount).text = fiatAmount?.formatAsCurrency(fiatSymbol)
        content.findViewById<TextView>(R.id.itemAssetFiatAmount).setVisible(!isUpdating, View.INVISIBLE)
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
