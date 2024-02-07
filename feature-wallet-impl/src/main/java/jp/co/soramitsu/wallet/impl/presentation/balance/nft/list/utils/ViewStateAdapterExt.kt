package jp.co.soramitsu.wallet.impl.presentation.balance.nft.list.utils

import jp.co.soramitsu.common.compose.models.ImageModel
import jp.co.soramitsu.common.compose.models.Loadable
import jp.co.soramitsu.common.compose.models.ScreenLayout
import jp.co.soramitsu.common.compose.models.TextModel
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.wallet.impl.presentation.balance.nft.list.models.NFTCollectionsScreenView

fun createShimmeredNFTCollectionsViewsList(
    screenLayout: ScreenLayout
): ArrayDeque<NFTCollectionsScreenView> {
    return ArrayDeque<NFTCollectionsScreenView>().apply {
        repeat(6) {
            ItemModel(
                screenLayout = screenLayout,
                thumbnail = Loadable.InProgress(),
                chainName = Loadable.InProgress(),
                title = Loadable.InProgress(),
                onItemClick = { },
            ).also { add(it) }
        }
    }
}

fun List<NFTCollection.Data<NFT.Light>>.toStableViewsList(
    screenLayout: ScreenLayout,
    onItemClick: (NFTCollection.Data<NFT.Light>) -> Unit
): ArrayDeque<NFTCollectionsScreenView> {
    val arrayDeque = ArrayDeque<NFTCollectionsScreenView>()

    for (collection in this) {
        collection.toScreenView(screenLayout) { onItemClick.invoke(collection) }
            .also { arrayDeque.add(it) }
    }

    return arrayDeque
}

fun NFTCollection.Data<NFT.Light>.toScreenView(
    screenLayout: ScreenLayout,
    onItemClick: () -> Unit
): NFTCollectionsScreenView.ItemModel {
    return ItemModel(
        screenLayout = screenLayout,
        thumbnail = Loadable.ReadyToRender(
            ImageModel.UrlWithFallbackOption(
                imageUrl.orEmpty(),
                ImageModel.ResId(R.drawable.drawable_fearless_bird)
            )
        ),
        chainName = Loadable.ReadyToRender(TextModel.SimpleString(chainName)),
        title = Loadable.ReadyToRender(TextModel.SimpleString(collectionName)),
        onItemClick = onItemClick
    ).run {
        val fractionAsString = "${this@toScreenView.balance}/${this@toScreenView.collectionSize}"

        val quantityTextModel = if (screenLayout === ScreenLayout.Grid) {
            TextModel.SimpleString(fractionAsString)
        } else TextModel.SimpleString(fractionAsString)

        NFTCollectionsScreenView.ItemModel.WithQuantityDecorator(
            initialItemModel = this,
            quantity = quantityTextModel
        )
    }
}

private class ItemModel(
    override val screenLayout: ScreenLayout,
    override val thumbnail: Loadable<ImageModel>,
    override val chainName: Loadable<TextModel>,
    override val title: Loadable<TextModel>,
    override val onItemClick: () -> Unit
): NFTCollectionsScreenView.ItemModel