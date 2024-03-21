package jp.co.soramitsu.wallet.impl.presentation.balance.nft.list.models

import jp.co.soramitsu.common.compose.models.ImageModel
import jp.co.soramitsu.common.compose.models.Loadable
import jp.co.soramitsu.common.compose.models.LoadableListPage
import jp.co.soramitsu.common.compose.models.ScreenLayout
import jp.co.soramitsu.common.compose.models.TextModel
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.nft.domain.models.NFTCollection

internal sealed interface ScreenModel {
    object PreviousPageLoading : ScreenModel,
        LoadableListPage.PreviousPageLoading<NFTCollectionsScreenView>

    object NextPageLoading : ScreenModel,
        LoadableListPage.NextPageLoading<NFTCollectionsScreenView>

    object Reloading : ScreenModel, LoadableListPage.Reloading<NFTCollectionsScreenView> {
        private const val SHIMMERED_ITEMS_COUNT = 6

        override val views: Collection<NFTCollectionsScreenView> =
            ArrayDeque<NFTCollectionsScreenView>(SHIMMERED_ITEMS_COUNT).apply {
                repeat(SHIMMERED_ITEMS_COUNT) {
                    add(
                        object : NFTCollectionsScreenView.ItemModel {
                            override val key: Any = it
                            override val screenLayout: ScreenLayout = ScreenLayout.Grid
                            override val thumbnail: Loadable<ImageModel> = Loadable.InProgress()
                            override val chainName: Loadable<TextModel> = Loadable.InProgress()
                            override val title: Loadable<TextModel> = Loadable.InProgress()
                            override val onItemClick = {}
                        }
                    )
                }
            }
    }

    class ReadyToRender(
        result: Collection<NFTCollection.Loaded>,
        screenLayout: ScreenLayout,
        onItemClick: (NFTCollection.Loaded.Result.Collection) -> Unit
    ) : ScreenModel, LoadableListPage.ReadyToRender<NFTCollectionsScreenView> {
        override val views: Collection<NFTCollectionsScreenView> =
            result.asSequence().filterIsInstance<NFTCollection.Loaded.Result.Collection>()
                .sortedBy { it.collectionName }
                .map { collection ->
                    ItemModel(
                        collection = collection,
                        screenLayout = screenLayout,
                        onItemClick = { onItemClick.invoke(collection) },
                    )
                }.ifEmpty { sequenceOf(NFTCollectionsScreenView.EmptyPlaceholder) }.toList()
    }
}

private class ItemModel(
    collection: NFTCollection.Loaded.Result.Collection,
    override val screenLayout: ScreenLayout,
    override val onItemClick: () -> Unit
) : NFTCollectionsScreenView.ItemModel.WithQuantityDecorator {

    override val key: Any = collection.contractAddress

    override val thumbnail: Loadable<ImageModel> =
        Loadable.ReadyToRender(
            ImageModel.UrlWithFallbackOption(
                collection.imageUrl,
                ImageModel.ResId(R.drawable.drawable_fearless_bird)
            )
        )

    override val chainName: Loadable<TextModel> =
        Loadable.ReadyToRender(
            TextModel.SimpleString(
                collection.chainName
            )
        )

    override val title: Loadable<TextModel> =
        Loadable.ReadyToRender(
            TextModel.SimpleString(
                collection.collectionName
            )
        )

    override val quantity: TextModel = if (screenLayout === ScreenLayout.List) {
        TextModel.ResIdWithArgs(
            R.string.nfts_collection_count,
            arrayOf(collection.balance, collection.collectionSize)
        )
    } else TextModel.SimpleString("${collection.balance}/${collection.collectionSize}")

}