package jp.co.soramitsu.nft.impl.presentation.collection.utils

import androidx.compose.runtime.snapshots.SnapshotStateList
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.models.ImageModel
import jp.co.soramitsu.common.compose.models.Loadable
import jp.co.soramitsu.common.compose.models.ScreenLayout
import jp.co.soramitsu.common.compose.models.TextModel
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.nft.impl.presentation.collection.models.NFTsScreenView

fun createShimmeredNFTViewsList(): SnapshotStateList<NFTsScreenView> {
    return SnapshotStateList<NFTsScreenView>().apply {
        ScreenHeader(
            thumbnail = Loadable.InProgress(),
            description = Loadable.InProgress()
        ).also { add(it) }

        SectionHeader(
            title = Loadable.InProgress()
        ).also { add(it) }

        repeat(6) {
            ItemModel(
                screenLayout = ScreenLayout.Grid,
                thumbnail = Loadable.InProgress(),
                title = Loadable.InProgress(),
                description = Loadable.InProgress(),
                onItemClick = {  },
            ).also { add(it) }
        }
    }
}

fun NFTCollection<NFT.Full>.toScreenViewStableList(
    onItemClick: (NFT.Full) -> Unit,
    onActionButtonClick: (NFT.Full) -> Unit,
): ArrayDeque<NFTsScreenView> {
    val arrayDeque = ArrayDeque<NFTsScreenView>()

    if (this !is NFTCollection.Data) {
        NFTsScreenView.EmptyPlaceHolder.also {
            arrayDeque.add(it)
        }

        return arrayDeque
    }

    val isCollectionUserOwned =
        tokens.firstOrNull()?.isUserOwnedToken == true

    if (isCollectionUserOwned) {
        ScreenHeader(
            thumbnail = Loadable.ReadyToRender(
                ImageModel.UrlWithFallbackOption(
                    imageUrl.orEmpty(),
                    ImageModel.Gif(R.drawable.animated_bird)
                )
            ),
            description = Loadable.ReadyToRender(
                description?.let { TextModel.SimpleString(it) }
            )
        ).also { arrayDeque.add(it) }

        SectionHeader(
            title = Loadable.ReadyToRender(
                TextModel.ResId(
                    R.string.nft_collection_my_nfts
                )
            )
        ).also { arrayDeque.add(it) }
    } else {
        SectionHeader(
            title = Loadable.ReadyToRender(
                TextModel.ResIdWithArgs(
                    R.string.nft_collection_available_nfts,
                    arrayOf(collectionName)
                )
            )
        ).also { arrayDeque.add(it) }
    }

    for(token in tokens) {
        arrayDeque.add(
            token.toScreenView(
                screenLayout = if (tokens.size > 1)
                    ScreenLayout.Grid
                else ScreenLayout.List,
                onItemClick = { onItemClick.invoke(token) },
                onActionButtonClick = { onActionButtonClick.invoke(token) }
            )
        )
    }

    return arrayDeque
}

private fun NFT.Full.toScreenView(
    screenLayout: ScreenLayout,
    onItemClick: () -> Unit,
    onActionButtonClick: () -> Unit
): NFTsScreenView.ItemModel {
    return ItemModel(
        screenLayout = screenLayout,
        thumbnail = Loadable.ReadyToRender(
            ImageModel.UrlWithFallbackOption(
                thumbnail,
                ImageModel.ResId(R.drawable.drawable_fearless_bird)
            )
        ),
        title = Loadable.ReadyToRender(
            TextModel.SimpleString(
                title.orEmpty()
            )
        ),
        description = Loadable.ReadyToRender(
            TextModel.SimpleString(
                description.orEmpty()
            )
        ),
        onItemClick = onItemClick,
    ).run {
        val buttonText = if (isUserOwnedToken) {
            TextModel.ResId(R.string.common_action_send)
        } else TextModel.ResId(R.string.common_share)

        val buttonImage = if (isUserOwnedToken) {
            ImageModel.ResId(R.drawable.ic_send_outlined)
        } else ImageModel.ResId(R.drawable.ic_share_arrow_white_24)

        NFTsScreenView.ItemModel.WithButtonDecorator(
            initialItemModel = this,
            buttonText = buttonText,
            buttonImage = buttonImage,
            onButtonClick = onActionButtonClick
        )
    }
}

private class ScreenHeader(
    override val thumbnail: Loadable<ImageModel>,
    override val description: Loadable<TextModel?>
): NFTsScreenView.ScreenHeader

private class SectionHeader(
    override val title: Loadable<TextModel>
): NFTsScreenView.SectionHeader

private class ItemModel(
    override val screenLayout: ScreenLayout,
    override val thumbnail: Loadable<ImageModel>,
    override val title: Loadable<TextModel>,
    override val description: Loadable<TextModel?>,
    override val onItemClick: () -> Unit
): NFTsScreenView.ItemModel