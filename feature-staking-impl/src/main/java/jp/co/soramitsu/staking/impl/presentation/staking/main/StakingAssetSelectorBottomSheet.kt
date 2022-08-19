package jp.co.soramitsu.staking.impl.presentation.staking.main

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import coil.ImageLoader
import coil.load
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.ClickHandler
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListSheetAdapter
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.HolderCreator
import jp.co.soramitsu.feature_wallet_api.R
import jp.co.soramitsu.staking.api.data.StakingAssetSelection
import jp.co.soramitsu.staking.impl.presentation.common.StakingAssetSelector

class StakingAssetSelectorBottomSheet(
    private val imageLoader: ImageLoader,
    context: Context,
    payload: Payload<StakingAssetSelector.StakingAssetSelectorModel>,
    onClicked: ClickHandler<StakingAssetSelector.StakingAssetSelectorModel>
) : DynamicListBottomSheet<StakingAssetSelector.StakingAssetSelectorModel>(
    context,
    payload,
    StakingAssetSelector.StakingAssetSelectorModel.DIFF_CALLBACK,
    onClicked
) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.wallet_assets)
    }

    override fun holderCreator(): HolderCreator<StakingAssetSelector.StakingAssetSelectorModel> = { parent ->
        StakingAssetSelectorHolder(parent.inflateChild(R.layout.item_asset_selector), imageLoader)
    }
}

private class StakingAssetSelectorHolder(
    parent: View,
    private val imageLoader: ImageLoader
) : DynamicListSheetAdapter.Holder<StakingAssetSelector.StakingAssetSelectorModel>(parent) {

    override fun bind(
        item: StakingAssetSelector.StakingAssetSelectorModel,
        isSelected: Boolean,
        handler: DynamicListSheetAdapter.Handler<StakingAssetSelector.StakingAssetSelectorModel>
    ) {
        super.bind(item, isSelected, handler)

        with(itemView) {
            findViewById<TextView>(R.id.itemAssetSelectorBalance).text = item.assetBalance
            findViewById<TextView>(R.id.itemAssetSelectorTokenName).text = item.tokenName
            findViewById<ImageView>(R.id.itemAssetSelectorIcon).load(item.imageUrl, imageLoader)
            findViewById<ImageView>(R.id.itemAssetSelectorCheckmark).setVisible(isSelected, falseState = View.INVISIBLE)
            (item.selectionItem as? StakingAssetSelection.Pool)?.let {
                findViewById<TextView>(R.id.itemAssetSelectorBadge).apply {
                    isVisible = true
                    text = it.type
                }
            }
        }
    }
}
