package jp.co.soramitsu.wallet.impl.presentation.balance.nft.list.models

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import jp.co.soramitsu.common.compose.models.ImageModel
import jp.co.soramitsu.common.compose.models.Loadable
import jp.co.soramitsu.common.compose.models.LoadableListPage
import jp.co.soramitsu.common.compose.models.ScreenLayout
import jp.co.soramitsu.common.compose.models.TextModel
import jp.co.soramitsu.common.compose.utils.PageScrollingCallback
import jp.co.soramitsu.feature_wallet_impl.R

@Immutable
class NFTCollectionsScreenModel(
    val areFiltersApplied: Boolean,
    val screenLayout: ScreenLayout,
    val loadableListPage: LoadableListPage<NFTCollectionsScreenView>,
    val onFiltersIconClick: () -> Unit,
    val onScreenLayoutChanged: (ScreenLayout) -> Unit,
    val pageScrollingCallback: PageScrollingCallback
)

@Stable
sealed interface NFTCollectionsScreenView {

    val contentType: Any

    @Immutable
    interface EmptyPlaceholder: NFTCollectionsScreenView {
        override val contentType: Any
            get() = 0

        val image: ImageModel.ResId

        val header: TextModel

        val body: TextModel

        companion object : EmptyPlaceholder {
            override val image: ImageModel.ResId =
                ImageModel.ResId(R.drawable.ic_screen_warning)

            override val header: TextModel =
                TextModel.ResId(R.string.nft_stub_title)

            override val body: TextModel =
                TextModel.ResId(R.string.nft_list_empty_message)
        }

    }

    @Immutable
    interface LoadingIndication: NFTCollectionsScreenView {
        override val contentType: Any
            get() = 1

        companion object: LoadingIndication
    }

    @Immutable
    interface ItemModel: NFTCollectionsScreenView {
        override val contentType: Any
            get() = 2

        val key: Any

        val screenLayout: ScreenLayout

        val thumbnail: Loadable<ImageModel>

        val chainName: Loadable<TextModel>

        val title: Loadable<TextModel>

        val onItemClick: () -> Unit

        interface WithQuantityDecorator: ItemModel{
            val quantity: TextModel
        }

    }

}