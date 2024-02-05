package jp.co.soramitsu.nft.impl.presentation.chooserecipient.contract

import jp.co.soramitsu.common.compose.component.AddressInputState
import jp.co.soramitsu.common.compose.component.ButtonViewState

data class ChooseNFTRecipientScreenState(
    val addressInputState: AddressInputState,
    val buttonState: ButtonViewState,
    val isHistoryAvailable: Boolean,
    val isLoading: Boolean
) {
    companion object {
        val default = ChooseNFTRecipientScreenState(
            addressInputState = AddressInputState("", "", ""),
            buttonState = ButtonViewState("", false),
            isHistoryAvailable = false,
            isLoading = true
        )
    }
}

interface ChooseNFTRecipientCallback {
    fun onAddressInput(input: String)

    fun onAddressInputClear()

    fun onNextClick()

    fun onQrClick()

    fun onHistoryClick()

    fun onWalletsClick()

    fun onPasteClick()

    /* Empty Callback */
    companion object: ChooseNFTRecipientCallback {
        override fun onAddressInput(input: String) = Unit
        override fun onAddressInputClear() = Unit
        override fun onNextClick() = Unit
        override fun onQrClick() = Unit
        override fun onHistoryClick() = Unit
        override fun onWalletsClick() = Unit
        override fun onPasteClick() = Unit
    }
}