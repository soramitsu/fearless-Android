package jp.co.soramitsu.nft.impl.presentation.collection.models

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import jp.co.soramitsu.common.compose.models.ImageModel
import jp.co.soramitsu.common.compose.models.Loadable
import jp.co.soramitsu.common.compose.models.ScreenLayout
import jp.co.soramitsu.common.compose.models.TextModel
import jp.co.soramitsu.common.compose.utils.PageScrollingCallback
import jp.co.soramitsu.feature_nft_impl.R

@Immutable
sealed interface LoadableListPage<T> {

    class PageReloading<T>: LoadableListPage<T>

    class PreviousPageLoading<T>: LoadableListPage<T>

    class NextPageLoading<T>: LoadableListPage<T>

    class ReadyToRender<T>(
        val data: List<T>
    ): LoadableListPage<T>

}

@Immutable
class NFTsScreenModel(
    val views: LoadableListPage<NFTsScreenView>,
    val pageScrollingCallback: PageScrollingCallback
)

@Stable
sealed interface NFTsScreenView {

    val contentType: Any

    @Immutable
    interface ScreenHeader : NFTsScreenView {
        override val contentType: Any
            get() = 0

        val key: Any

        val thumbnail: Loadable<ImageModel>

        val description: Loadable<TextModel?>
    }

    @Immutable
    interface SectionHeader : NFTsScreenView {
        override val contentType: Any
            get() = 1

        val key: Any

        val title: Loadable<TextModel>
    }

    @Immutable
    interface ItemModel : NFTsScreenView {
        override val contentType: Any
            get() = 2

        val key: Any

        val screenLayout: ScreenLayout

        val thumbnail: Loadable<ImageModel>

        val title: Loadable<TextModel>

        val description: Loadable<TextModel?>

        val onItemClick: () -> Unit

        class WithButtonDecorator(
            initialItemModel: ItemModel,
            val buttonText: TextModel,
            val buttonImage: ImageModel.ResId,
            val onButtonClick: () -> Unit
        ) : ItemModel by initialItemModel
    }

    @Immutable
    interface LoadingIndication : NFTsScreenView {
        override val contentType: Any
            get() = 3

        companion object : LoadingIndication
    }

    @Immutable
    interface EmptyPlaceHolder : NFTsScreenView {

        override val contentType: Any
            get() = 4

        val image: ImageModel.ResId

        val header: TextModel

        val body: TextModel

        companion object : EmptyPlaceHolder {
            override val image: ImageModel.ResId =
                ImageModel.ResId(R.drawable.ic_screen_warning)

            override val header: TextModel =
                TextModel.ResId(R.string.nft_stub_text)

            override val body: TextModel =
                TextModel.ResId(R.string.nft_list_empty_message)
        }
    }
}
