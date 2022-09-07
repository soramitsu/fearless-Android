package jp.co.soramitsu.feature_wallet_impl.presentation.balance.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.ImageLoader
import coil.load
import java.math.BigDecimal
import jp.co.soramitsu.common.list.PayloadGenerator
import jp.co.soramitsu.common.list.resolvePayload
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.shape.addRipple
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.common.view.shape.getCutLeftBottomCornerDrawableFromColors
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.ItemAssetWithShimmerBinding
import jp.co.soramitsu.feature_wallet_impl.presentation.model.AssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.model.AssetWithStateModel
import kotlinx.android.extensions.LayoutContainer

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
        val binding = ItemAssetWithShimmerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AssetViewHolder(binding, imageLoader)
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
    val binding: ItemAssetWithShimmerBinding,
    private val imageLoader: ImageLoader,
) : RecyclerView.ViewHolder(binding.root), LayoutContainer {

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
        binding.apply {
            binding.itemAssetContent.root.alpha = when {
                asset.isSupported -> 1.0F
                else -> 0.4f
            }
            itemAssetContent.apply {
                itemAssetImage.load(asset.token.configuration.iconUrl, imageLoader)
                itemAssetNetwork.text = asset.token.configuration.name
                itemAssetToken.text = asset.token.configuration.symbol
                networkBadge.setText(asset.token.configuration.chainName)
                networkBadge.setIcon(asset.token.configuration.chainIcon, imageLoader)
                root.setOnClickListener { itemHandler.assetClicked(asset) }
                chainAssetNameBadge.text = asset.chainAccountName
                chainAssetNameBadge.background = root.context.getCutLeftBottomCornerDrawableFromColors()
            }
            itemAssetShimmer.apply {
                itemAssetImage.load(asset.token.configuration.iconUrl, imageLoader)
                itemAssetNetwork.text = asset.token.configuration.name
                itemAssetToken.text = asset.token.configuration.symbol
                networkBadge.setText(asset.token.configuration.chainName)
                networkBadge.setIcon(asset.token.configuration.chainIcon, imageLoader)
                root.setOnClickListener { itemHandler.assetClicked(asset) }
                chainAssetNameBadge.text = asset.chainAccountName
                chainAssetNameBadge.background = root.context.getCutLeftBottomCornerDrawableFromColors()
            }
        }
        bindState(model)
    }

    fun bindState(model: AssetWithStateModel) {
        val asset = model.asset
        val state = model.state
        val isShimmerVisible = state.chainUpdate != false
        binding.apply {
            itemAssetContent.apply {
                itemAssetImage.setVisible(state.chainUpdate == false, View.INVISIBLE)
                itemAssetNetwork.setVisible(state.chainUpdate == false, View.INVISIBLE)
                itemAssetToken.setVisible(state.chainUpdate == false, View.INVISIBLE)
                networkBadge.isVisible = !asset.token.configuration.isNative && state.chainUpdate == false
                testnetBadge.isVisible = asset.token.configuration.isTestNet == true && state.chainUpdate == false
                chainAssetNameBadge.isVisible = !asset.chainAccountName.isNullOrEmpty() && state.chainUpdate == false
            }
            itemAssetShimmer.apply {
                itemAssetImage.setVisible(isShimmerVisible, View.INVISIBLE)
                itemAssetNetwork.setVisible(isShimmerVisible, View.INVISIBLE)
                itemAssetToken.setVisible(isShimmerVisible, View.INVISIBLE)
                networkBadge.isVisible = !asset.token.configuration.isNative && isShimmerVisible
                testnetBadge.isVisible = asset.token.configuration.isTestNet == true && isShimmerVisible
                chainAssetNameBadge.isVisible = !asset.chainAccountName.isNullOrEmpty() && isShimmerVisible
            }
        }
        bindFiatInfo(model)

        bindRecentChange(model)

        bindTotal(model)
    }

    fun bindTotal(model: AssetWithStateModel) {
        val asset = model.asset
        binding.apply {
            itemAssetShimmer.apply {
                itemAssetBalanceShimmer.isVisible = asset.total == null && model.state.isBalanceUpdating
                itemAssetBalance.setVisible(asset.total != null && model.state.isBalanceUpdating, View.INVISIBLE)
                itemAssetBalance.text = asset.total.orZero().format()
            }
            itemAssetContent.apply {
                itemAssetBalance.text = asset.total.orZero().format()
                itemAssetBalance.setVisible(!model.state.isBalanceUpdating, View.INVISIBLE)
            }
        }

        bindFiatAmount(asset.fiatAmount, asset.token.fiatSymbol, model.state.isFiatUpdating)
    }

    fun bindRecentChange(model: AssetWithStateModel) {
        val asset = model.asset
        binding.apply {
            itemAssetShimmer.apply {
                itemAssetRateChange.setVisible(
                    asset.token.fiatRate != null && asset.token.recentRateChange != null && model.state.isRateUpdating,
                    View.INVISIBLE
                )
                itemAssetRateChange.setTextColorRes(R.color.white_64)
                itemAssetRateChange.text = asset.token.recentRateChange?.formatAsChange()
            }

            itemAssetContent.apply {
                itemAssetRateChange.setTextColorRes(asset.token.rateChangeColorRes)
                itemAssetRateChange.text = asset.token.recentRateChange?.formatAsChange()
                itemAssetRateChange.setVisible(!model.state.isRateUpdating, View.INVISIBLE)
            }
        }
    }

    fun bindFiatInfo(model: AssetWithStateModel) {
        val asset = model.asset
        binding.apply {
            itemAssetShimmer.apply {
                itemAssetRateShimmer.isVisible = asset.token.fiatRate == null && model.state.isRateUpdating
                itemAssetRate.setVisible(asset.token.fiatRate != null && model.state.isRateUpdating, View.INVISIBLE)
                itemAssetRate.text = asset.token.fiatRate?.formatAsCurrency(asset.token.fiatSymbol)
            }

            itemAssetContent.apply {
                itemAssetRate.text = asset.token.fiatRate?.formatAsCurrency(asset.token.fiatSymbol)
                itemAssetRate.setVisible(asset.token.fiatRate != null && !model.state.isRateUpdating, View.INVISIBLE)
            }
        }

        bindFiatAmount(asset.fiatAmount, asset.token.fiatSymbol, model.state.isFiatUpdating)
    }

    private fun bindFiatAmount(fiatAmount: BigDecimal?, fiatSymbol: String?, isUpdating: Boolean) {
        binding.apply {
            itemAssetShimmer.apply {
                itemAssetFiatAmountShimmer.isVisible = fiatAmount == null && isUpdating
                itemAssetFiatAmount.setVisible(fiatAmount != null && isUpdating, View.INVISIBLE)
                itemAssetFiatAmount.text = fiatAmount?.formatAsCurrency(fiatSymbol)
            }

            itemAssetContent.apply {
                itemAssetFiatAmount.text = fiatAmount?.formatAsCurrency(fiatSymbol)
                itemAssetFiatAmount.setVisible(!isUpdating, View.INVISIBLE)
            }
        }
    }

    override val containerView: View
        get() = binding.root
}

private object AssetDiffCallback : DiffUtil.ItemCallback<AssetWithStateModel>() {
    override fun areItemsTheSame(oldItem: AssetWithStateModel, newItem: AssetWithStateModel): Boolean {
        return oldItem.asset.token.configuration.chainToSymbol == newItem.asset.token.configuration.chainToSymbol &&
            oldItem.asset.metaId in listOf(null, newItem.asset.metaId)
    }

    override fun areContentsTheSame(oldItem: AssetWithStateModel, newItem: AssetWithStateModel): Boolean {
        return oldItem == newItem
    }

    override fun getChangePayload(oldItem: AssetWithStateModel, newItem: AssetWithStateModel): Any {
        return AssetPayloadGenerator.diff(oldItem, newItem)
    }
}

private object AssetPayloadGenerator : PayloadGenerator<AssetWithStateModel>(
    fiatRateExtractor, recentChangeExtractor, totalExtractor, stateExtractor
)
