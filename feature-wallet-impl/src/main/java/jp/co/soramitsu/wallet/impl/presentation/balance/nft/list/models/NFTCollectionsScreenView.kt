package jp.co.soramitsu.wallet.impl.presentation.balance.nft.list.models

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshots.SnapshotStateList
import jp.co.soramitsu.common.compose.models.ImageModel
import jp.co.soramitsu.common.compose.models.Loadable
import jp.co.soramitsu.common.compose.models.ScreenLayout
import jp.co.soramitsu.common.compose.models.TextModel
import jp.co.soramitsu.feature_nft_impl.R
import jp.co.soramitsu.common.compose.utils.PageScrollingCallback

@Immutable
class NFTCollectionsScreenModel(
    val areFiltersApplied: Boolean,
    val views: SnapshotStateList<NFTCollectionsScreenView>,
    val onFiltersIconClick: () -> Unit,
    val onScreenLayoutChanged: (ScreenLayout) -> Unit,
    val pageScrollingCallback: PageScrollingCallback
)

@Stable
sealed interface NFTCollectionsScreenView {

    @Immutable
    interface EmptyPlaceholder: NFTCollectionsScreenView {

        val image: ImageModel.ResId

        val header: TextModel

        val body: TextModel

        companion object : EmptyPlaceholder {
            override val image: ImageModel.ResId =
                ImageModel.ResId(R.drawable.ic_screen_warning)

            override val header: TextModel =
                TextModel.ResId(R.string.nft_stub_text)

            override val body: TextModel =
                TextModel.ResId(R.string.nft_list_empty_message)
        }

    }

    @Immutable
    interface LoadingIndication: NFTCollectionsScreenView {
        companion object: LoadingIndication
    }

    @Immutable
    interface ItemModel: NFTCollectionsScreenView {

        val screenLayout: ScreenLayout

        val thumbnail: Loadable<ImageModel>

        val chainName: Loadable<TextModel>

        val title: Loadable<TextModel>

        val onItemClick: () -> Unit

        class WithQuantityDecorator(
            initialItemModel: ItemModel,
            val quantity: TextModel
        ): ItemModel by initialItemModel

    }

}