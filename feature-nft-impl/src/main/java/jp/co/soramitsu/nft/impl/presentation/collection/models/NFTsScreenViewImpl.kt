package jp.co.soramitsu.nft.impl.presentation.collection.models

import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.models.ImageModel
import jp.co.soramitsu.common.compose.models.Loadable
import jp.co.soramitsu.common.compose.models.LoadableListPage
import jp.co.soramitsu.common.compose.models.ScreenLayout
import jp.co.soramitsu.common.compose.models.TextModel
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.domain.models.NFTCollectionResult

internal sealed interface ScreenModel {
    object PreviousPageLoading :
        ScreenModel,
        LoadableListPage.PreviousPageLoading<NFTsScreenView>

    object NextPageLoading :
        ScreenModel,
        LoadableListPage.NextPageLoading<NFTsScreenView>

    object Reloading : ScreenModel, LoadableListPage.Reloading<NFTsScreenView> {
        private const val SHIMMERED_ITEMS_COUNT = 6

        override val views: Collection<NFTsScreenView> =
            ArrayDeque<NFTsScreenView>(SHIMMERED_ITEMS_COUNT.plus(2)).apply {
                object : NFTsScreenView.ScreenHeader {
                    override val key: Any = "ScreenHeader"
                    override val thumbnail: Loadable<ImageModel> = Loadable.InProgress()
                    override val description: Loadable<TextModel?> = Loadable.InProgress()
                }.also { add(it) }

                object : NFTsScreenView.SectionHeader {
                    override val key: Any = "SectionHeader"
                    override val title: Loadable<TextModel> = Loadable.InProgress()
                }.also { add(it) }

                repeat(SHIMMERED_ITEMS_COUNT) {
                    add(
                        object : NFTsScreenView.ItemModel {
                            override val key: Any = it
                            override val screenLayout: ScreenLayout = ScreenLayout.Grid
                            override val thumbnail: Loadable<ImageModel> = Loadable.InProgress()
                            override val title: Loadable<TextModel> = Loadable.InProgress()
                            override val description: Loadable<TextModel?> = Loadable.InProgress()
                            override val onItemClick = {}
                        }
                    )
                }
            }
    }

    class ReadyToRender(
        result: NFTCollectionResult,
        onItemClick: (NFT) -> Unit,
        onActionButtonClick: (NFT) -> Unit
    ) : ScreenModel, LoadableListPage.ReadyToRender<NFTsScreenView> {
        override val views: Collection<NFTsScreenView> =
            ArrayDeque<NFTsScreenView>().apply {
                if (result !is NFTCollectionResult.Collection.WithTokens) {
                    add(NFTsScreenView.EmptyPlaceHolder)
                    return@apply
                }

                val screenLayout = if (result.tokens.count() > 1) {
                    ScreenLayout.Grid
                } else {
                    ScreenLayout.List
                }

                var isUserOwnedTokensSectionHeaderApplied = false
                var isAllTokensSectionHeaderApplied = false

                result.tokens.sortedBy { !it.isUserOwnedToken }.forEach { token ->
                    when {
                        !isUserOwnedTokensSectionHeaderApplied && token.isUserOwnedToken -> {
                            add(ScreenHeader(result))
                            add(SectionHeader(token))

                            isUserOwnedTokensSectionHeaderApplied = true
                        }

                        !isAllTokensSectionHeaderApplied && !token.isUserOwnedToken -> {
                            add(SectionHeader(token))

                            isAllTokensSectionHeaderApplied = true
                        }
                    }

                    add(
                        ItemModel(
                            token = token,
                            screenLayout = screenLayout,
                            onItemClick = { onItemClick.invoke(token) },
                            onButtonClick = { onActionButtonClick.invoke(token) }
                        )
                    )
                }

                if (isEmpty()) {
                    clear()
                    add(NFTsScreenView.EmptyPlaceHolder)
                }
            }
    }
}

private class ScreenHeader(
    collection: NFTCollectionResult.Collection
) : NFTsScreenView.ScreenHeader {

    override val key: Any = R.drawable.animated_bird

    override val thumbnail: Loadable<ImageModel> = Loadable.ReadyToRender(
        ImageModel.UrlWithFallbackOption(
            collection.imageUrl,
            ImageModel.Gif(R.drawable.animated_bird)
        )
    )

    override val description: Loadable<TextModel?> = Loadable.ReadyToRender(
        TextModel.SimpleString(
            collection.description
        )
    )
}

private class SectionHeader(
    token: NFT
) : NFTsScreenView.SectionHeader {

    override val key: Any = if (token.isUserOwnedToken) {
        R.string.nft_collection_my_nfts
    } else {
        R.string.nft_collection_available_nfts
        }

    override val title: Loadable<TextModel> =
        Loadable.ReadyToRender(
            if (token.isUserOwnedToken) {
                TextModel.ResId(R.string.nft_collection_my_nfts)
            } else {
                TextModel.ResIdWithArgs(
                R.string.nft_collection_available_nfts,
                arrayOf(token.collectionName)
            )
                }
        )
}

private class ItemModel(
    token: NFT,
    override val screenLayout: ScreenLayout,
    override val onItemClick: () -> Unit,
    override val onButtonClick: () -> Unit
) : NFTsScreenView.ItemModel.WithButtonDecorator {

    override val key: Any = token.tokenId

    override val thumbnail: Loadable<ImageModel> = Loadable.ReadyToRender(
        ImageModel.UrlWithFallbackOption(
            token.thumbnail,
            ImageModel.ResId(R.drawable.drawable_fearless_bird)
        )
    )

    override val title: Loadable<TextModel> = Loadable.ReadyToRender(
        TextModel.SimpleString(
            token.title
        )
    )

    override val description: Loadable<TextModel?> = Loadable.ReadyToRender(
        TextModel.SimpleString(
            token.description
        )
    )

    override val buttonText: TextModel = if (token.isUserOwnedToken) {
        TextModel.ResId(R.string.common_action_send)
    } else {
        TextModel.ResId(R.string.common_share) }

    override val buttonImage: ImageModel.ResId = if (token.isUserOwnedToken) {
        ImageModel.ResId(R.drawable.ic_send_outlined)
    } else {
        ImageModel.ResId(R.drawable.ic_share_arrow_white_24) }
}
