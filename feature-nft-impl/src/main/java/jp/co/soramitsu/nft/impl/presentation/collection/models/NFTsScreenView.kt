package jp.co.soramitsu.nft.impl.presentation.collection.models

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshots.SnapshotStateList
import jp.co.soramitsu.common.compose.models.ImageModel
import jp.co.soramitsu.common.compose.models.Loadable
import jp.co.soramitsu.common.compose.models.ScreenLayout
import jp.co.soramitsu.common.compose.models.TextModel
import jp.co.soramitsu.common.compose.utils.PageScrollingCallback
import jp.co.soramitsu.feature_nft_impl.R

@Immutable
class NFTsScreenModel(
    val views: SnapshotStateList<NFTsScreenView>,
    val pageScrollingCallback: PageScrollingCallback
)

@Stable
sealed interface NFTsScreenView {

    @Immutable
    interface ScreenHeader : NFTsScreenView {

        val thumbnail: Loadable<ImageModel>

        val description: Loadable<TextModel?>
    }

    @Immutable
    interface SectionHeader : NFTsScreenView {

        val title: Loadable<TextModel>
    }

    @Immutable
    interface ItemModel : NFTsScreenView {

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
        companion object : LoadingIndication
    }

    @Immutable
    interface EmptyPlaceHolder : NFTsScreenView {

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
