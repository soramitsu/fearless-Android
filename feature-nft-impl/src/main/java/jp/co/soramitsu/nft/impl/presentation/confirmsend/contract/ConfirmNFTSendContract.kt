package jp.co.soramitsu.nft.impl.presentation.confirmsend.contract

import androidx.compose.runtime.Stable
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.models.ImageModel
import jp.co.soramitsu.common.compose.models.Loadable

@Stable
data class ConfirmNFTSendScreenState(
    val thumbnailImageModel: Loadable<ImageModel>,
    val fromInfoItem: TitleValueViewState? = null,
    val toInfoItem: TitleValueViewState? = null,
    val collectionInfoItem: TitleValueViewState? = null,
    val feeInfoItem: TitleValueViewState? = null,
    val buttonState: ButtonViewState,
    val isLoading: Boolean = false
) {
    companion object {
        val default = ConfirmNFTSendScreenState(
            thumbnailImageModel = Loadable.InProgress(),
            buttonState = ButtonViewState("", false)
        )
    }

    val tableItems = listOfNotNull(
        fromInfoItem,
        toInfoItem,
        collectionInfoItem,
        feeInfoItem
    )
}

@Stable
interface ConfirmNFTSendCallback {
    fun onConfirmClick()

    fun onItemClick(code: Int)

    /* Empty Callback */
    companion object: ConfirmNFTSendCallback {
        override fun onConfirmClick() = Unit
        override fun onItemClick(code: Int) = Unit
    }
}