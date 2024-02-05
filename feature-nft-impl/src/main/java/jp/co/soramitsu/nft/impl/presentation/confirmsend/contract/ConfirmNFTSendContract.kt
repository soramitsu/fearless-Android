package jp.co.soramitsu.nft.impl.presentation.confirmsend.contract

import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState

data class ConfirmNFTSendScreenState(
    val tokenThumbnailIconUrl: String? = null,
    val fromInfoItem: TitleValueViewState? = null,
    val toInfoItem: TitleValueViewState? = null,
    val collectionInfoItem: TitleValueViewState? = null,
    val feeInfoItem: TitleValueViewState? = null,
    val buttonState: ButtonViewState,
    val isLoading: Boolean = false
) {
    companion object {
        val default = ConfirmNFTSendScreenState(
            tokenThumbnailIconUrl = null,
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

interface ConfirmNFTSendCallback {
    fun onConfirmClick()

    fun onItemClick(code: Int)

    /* Empty Callback */
    companion object: ConfirmNFTSendCallback {
        override fun onConfirmClick() = Unit
        override fun onItemClick(code: Int) = Unit
    }
}