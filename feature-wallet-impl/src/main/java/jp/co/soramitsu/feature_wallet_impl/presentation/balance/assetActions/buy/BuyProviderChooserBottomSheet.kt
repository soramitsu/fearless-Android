package jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions.buy

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.ClickHandler
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListSheetAdapter
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.HolderCreator
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.ReferentialEqualityDiffCallBack
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

typealias BuyProvider = BuyTokenRegistry.Provider<*>

class BuyProviderChooserBottomSheet(
    context: Context,
    providers: List<BuyProvider>,
    private val asset: Chain.Asset,
    onClick: ClickHandler<BuyProvider>,
) : DynamicListBottomSheet<BuyProvider>(
    context,
    Payload(providers),
    ReferentialEqualityDiffCallBack(),
    onClick
) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(context.getString(R.string.wallet_asset_buy_with, asset.symbol))
    }

    override fun holderCreator(): HolderCreator<BuyProvider> = {
        BuyProviderHolder(it.inflateChild(R.layout.item_sheet_buy_provider))
    }
}

private class BuyProviderHolder(
    itemView: View,
) : DynamicListSheetAdapter.Holder<BuyProvider>(itemView) {

    override fun bind(item: BuyProvider, isSelected: Boolean, handler: DynamicListSheetAdapter.Handler<BuyProvider>) {
        super.bind(item, isSelected, handler)

        val text = itemView.findViewById<TextView>(R.id.itemSheetBuyProviderText)
        val image = itemView.findViewById<ImageView>(R.id.itemSheetBuyProviderImage)

        text.text = item.name
        image.setImageResource(item.icon)
    }
}
