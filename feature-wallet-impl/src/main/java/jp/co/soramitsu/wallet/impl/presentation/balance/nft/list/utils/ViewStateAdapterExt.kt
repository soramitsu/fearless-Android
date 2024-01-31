package jp.co.soramitsu.wallet.impl.presentation.balance.nft.list.utils

import androidx.compose.runtime.snapshots.SnapshotStateList
import jp.co.soramitsu.common.compose.models.ImageModel
import jp.co.soramitsu.common.compose.models.Loadable
import jp.co.soramitsu.common.compose.models.ScreenLayout
import jp.co.soramitsu.common.compose.models.TextModel
import jp.co.soramitsu.nft.domain.models.NFTCollection
<<<<<<< HEAD
import jp.co.soramitsu.nft.impl.presentation.collection.utils.SHIMMER_VIEW_ARRAY_COUNT
=======
>>>>>>> feature/nft
import jp.co.soramitsu.wallet.impl.presentation.balance.nft.list.models.NFTCollectionsScreenView

fun createShimmeredNFTCollectionsViewsList(
    screenLayout: ScreenLayout
): SnapshotStateList<NFTCollectionsScreenView> {
    return SnapshotStateList<NFTCollectionsScreenView>().apply {
<<<<<<< HEAD
        repeat(SHIMMER_VIEW_ARRAY_COUNT) {
=======
        repeat(6) {
>>>>>>> feature/nft
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

fun List<NFTCollection<NFTCollection.NFT.Light>>.toStableViewsList(
    screenLayout: ScreenLayout,
    onItemClick: (NFTCollection<NFTCollection.NFT.Light>) -> Unit
): SnapshotStateList<NFTCollectionsScreenView> {
    val snapshotStateList = SnapshotStateList<NFTCollectionsScreenView>()

    for (collection in this) {
        collection.toScreenView(screenLayout) { onItemClick.invoke(collection) }
            .also { snapshotStateList.add(it) }
    }

    return snapshotStateList
}

fun NFTCollection<NFTCollection.NFT.Light>.toScreenView(
    screenLayout: ScreenLayout,
    onItemClick: () -> Unit
): NFTCollectionsScreenView.ItemModel {
    return ItemModel(
        screenLayout = screenLayout,
        thumbnail = Loadable.ReadyToRender(ImageModel.Url(imageUrl.orEmpty())),
        chainName = Loadable.ReadyToRender(TextModel.SimpleString(chainName)),
        title = Loadable.ReadyToRender(TextModel.SimpleString(collectionName)),
        onItemClick = onItemClick
    ).run {
        val fractionAsString = "${this@toScreenView.tokens.size}/${this@toScreenView.collectionSize}"

        val quantityTextModel = if (screenLayout === ScreenLayout.Grid) {
            TextModel.SimpleString(fractionAsString)
        } else {
            TextModel.SimpleString(fractionAsString)
        }

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
) : NFTCollectionsScreenView.ItemModel
