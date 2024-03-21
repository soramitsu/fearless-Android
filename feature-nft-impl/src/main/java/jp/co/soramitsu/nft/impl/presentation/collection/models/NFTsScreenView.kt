package jp.co.soramitsu.nft.impl.presentation.collection.models

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import jp.co.soramitsu.common.compose.models.ImageModel
import jp.co.soramitsu.common.compose.models.Loadable
import jp.co.soramitsu.common.compose.models.ScreenLayout
import jp.co.soramitsu.common.compose.models.TextModel
import jp.co.soramitsu.feature_nft_impl.R

@Stable
sealed interface NFTsScreenView {

    val contentType: Any

    val key: Any

    @Immutable
    interface ScreenHeader : NFTsScreenView {
        override val contentType: Any
            get() = 0

        val thumbnail: Loadable<ImageModel>

        val description: Loadable<TextModel?>
    }

    @Immutable
    interface SectionHeader : NFTsScreenView {
        override val contentType: Any
            get() = 1

        val title: Loadable<TextModel>
    }

    @Immutable
    interface ItemModel : NFTsScreenView {
        override val contentType: Any
            get() = 2

        val screenLayout: ScreenLayout

        val thumbnail: Loadable<ImageModel>

        val title: Loadable<TextModel>

        val description: Loadable<TextModel?>

        val onItemClick: () -> Unit

        interface WithButtonDecorator : ItemModel {

            val buttonText: TextModel

            val buttonImage: ImageModel.ResId

            val onButtonClick: () -> Unit
        }
    }

    @Immutable
    interface LoadingIndication : NFTsScreenView {
        override val contentType: Any
            get() = 3

        override val key: Any
            get() = -1

        companion object : LoadingIndication
    }

    @Immutable
    interface EmptyPlaceHolder : NFTsScreenView {

        override val contentType: Any
            get() = 4

        override val key: Any
            get() = 0

        val image: ImageModel.ResId

        val header: TextModel

        val body: TextModel

        companion object : EmptyPlaceHolder {
            override val image: ImageModel.ResId =
                ImageModel.ResId(R.drawable.ic_screen_warning)

            override val header: TextModel =
                TextModel.ResId(R.string.nft_stub_title)

            override val body: TextModel =
                TextModel.ResId(R.string.nft_list_empty_message)
        }
    }

    companion object;
}
