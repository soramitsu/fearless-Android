package jp.co.soramitsu.nft.impl.presentation.collection.models

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshots.SnapshotStateList
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.models.ImageModel
import jp.co.soramitsu.common.compose.models.Loadable
import jp.co.soramitsu.common.compose.models.ScreenLayout
import jp.co.soramitsu.common.compose.models.TextModel
import jp.co.soramitsu.common.compose.utils.PageScrollingCallback
import jp.co.soramitsu.common.presentation.LoadingState

@Immutable
class NFTsScreenModel(
    val toolbarState: State<LoadingState<ToolbarViewState>>,
    val views: SnapshotStateList<NFTsScreenView>,
    val pageScrollingCallback: PageScrollingCallback
)

@Stable
sealed interface NFTsScreenView {

    @Immutable
    interface ScreenHeader: NFTsScreenView {

        val thumbnail: Loadable<ImageModel?>

        val description: Loadable<TextModel?>

    }

    @Immutable
    interface SectionHeader: NFTsScreenView {

        val title: Loadable<TextModel>

    }

    @Immutable
    interface ItemModel: NFTsScreenView {

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
        ): ItemModel by initialItemModel

    }

}