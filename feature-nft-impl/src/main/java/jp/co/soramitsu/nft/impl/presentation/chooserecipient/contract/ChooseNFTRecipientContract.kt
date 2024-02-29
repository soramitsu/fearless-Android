package jp.co.soramitsu.nft.impl.presentation.chooserecipient.contract

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Stable
import jp.co.soramitsu.common.compose.component.AddressInputState
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.feature_nft_impl.R

@Stable
data class ChooseNFTRecipientScreenState(
    val selectedWalletIcon: Drawable?,
    val addressInputState: AddressInputState,
    val buttonState: ButtonViewState,
    val feeInfoState: FeeInfoViewState,
    val isLoading: Boolean
) {
    companion object {
        val default = ChooseNFTRecipientScreenState(
            selectedWalletIcon = null,
            addressInputState = AddressInputState(
                "",
                "",
                R.drawable.ic_address_placeholder,
                editable = false,
                showClear = false
            ),
            buttonState = ButtonViewState("", false),
            feeInfoState = FeeInfoViewState.default,
            isLoading = true
        )
    }
}

@Stable
interface ChooseNFTRecipientCallback {
    fun onAddressInput(input: String)

    fun onAddressInputClear()

    fun onNextClick()

    fun onQrClick()

    fun onContactsClick()

    fun onWalletsClick()

    fun onPasteClick()

    // Empty Callback
    companion object : ChooseNFTRecipientCallback {
        override fun onAddressInput(input: String) = Unit
        override fun onAddressInputClear() = Unit
        override fun onNextClick() = Unit
        override fun onQrClick() = Unit
        override fun onContactsClick() = Unit
        override fun onWalletsClick() = Unit
        override fun onPasteClick() = Unit
    }
}
