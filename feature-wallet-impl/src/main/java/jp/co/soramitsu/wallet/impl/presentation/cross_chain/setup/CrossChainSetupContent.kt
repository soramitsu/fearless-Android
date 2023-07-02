package jp.co.soramitsu.wallet.impl.presentation.cross_chain.setup

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.AddressInput
import jp.co.soramitsu.common.compose.component.AddressInputState
import jp.co.soramitsu.common.compose.component.AmountInput
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.Badge
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.FeeInfo
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.QuickAmountInput
import jp.co.soramitsu.common.compose.component.QuickInput
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.compose.component.SelectorWithBorder
import jp.co.soramitsu.common.compose.component.ToolbarBottomSheet
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.WarningInfo
import jp.co.soramitsu.common.compose.component.WarningInfoState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.feature_wallet_impl.R

data class CrossChainSetupViewState(
    val toolbarState: ToolbarViewState,
    val addressInputState: AddressInputState,
    val amountInputState: AmountInputViewState,
    val originChainSelectorState: SelectorState,
    val destinationChainSelectorState: SelectorState,
    val originFeeInfoState: FeeInfoViewState,
    val destinationFeeInfoState: FeeInfoViewState?,
    val warningInfoState: WarningInfoState?,
    val buttonState: ButtonViewState,
    val walletIcon: Drawable?,
    val isSoftKeyboardOpen: Boolean,
    val heightDiffDp: Dp
)

interface CrossChainSetupScreenInterface {
    fun onNavigationClick()
    fun onAddressInput(input: String)
    fun onAddressInputClear()
    fun onAmountInput(input: BigDecimal?)
    fun onDestinationChainClick()
    fun onAssetClick()
    fun onNextClick()
    fun onQrClick()
    fun onHistoryClick()
    fun onPasteClick()
    fun onMyWalletsClick()
    fun onAmountFocusChanged(isFocused: Boolean)
    fun onQuickAmountInput(input: Double)
    fun onWarningInfoClick()
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CrossChainSetupContent(
    state: CrossChainSetupViewState,
    callback: CrossChainSetupScreenInterface
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val showQuickInput = state.amountInputState.isFocused && state.isSoftKeyboardOpen
    BottomSheetScreen {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = state.heightDiffDp)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 115.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                ToolbarBottomSheet(
                    title = stringResource(id = R.string.common_title_cross_chain),
                    onNavigationClick = callback::onNavigationClick
                )

                MarginVertical(margin = 16.dp)
                SelectorWithBorder(
                    state = state.originChainSelectorState
                )

                MarginVertical(margin = 12.dp)
                AmountInput(
                    state = state.amountInputState,
                    borderColorFocused = colorAccentDark,
                    onTokenClick = callback::onAssetClick,
                    onInput = callback::onAmountInput,
                    onInputFocusChange = callback::onAmountFocusChanged
                )

                MarginVertical(margin = 12.dp)
                SelectorWithBorder(
                    state = state.destinationChainSelectorState,
                    onClick = callback::onDestinationChainClick
                )

                MarginVertical(margin = 12.dp)
                AddressInput(
                    state = state.addressInputState,
                    onInput = callback::onAddressInput,
                    onInputClear = callback::onAddressInputClear,
                    onPaste = callback::onPasteClick
                )
                MarginVertical(margin = 8.dp)
                AddressActions(
                    walletIcon = state.walletIcon,
                    callback = callback
                )

                state.warningInfoState?.let {
                    MarginVertical(margin = 12.dp)
                    WarningInfo(state = it, onClick = callback::onWarningInfoClick)
                }
                MarginVertical(margin = 12.dp)
                FeeInfo(state = state.originFeeInfoState, modifier = Modifier.defaultMinSize(minHeight = 52.dp))
                if (state.destinationFeeInfoState != null) {
                    FeeInfo(state = state.destinationFeeInfoState, modifier = Modifier.defaultMinSize(minHeight = 52.dp))
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
            ) {
                MarginVertical(margin = 12.dp)
                AccentButton(
                    state = state.buttonState,
                    onClick = {
                        keyboardController?.hide()
                        callback.onNextClick()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(48.dp)
                )
                MarginVertical(margin = 12.dp)
                if (showQuickInput) {
                    QuickInput(
                        values = QuickAmountInput.values(),
                        onQuickAmountInput = {
                            keyboardController?.hide()
                            callback.onQuickAmountInput(it)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddressActions(
    walletIcon: Drawable?,
    callback: CrossChainSetupScreenInterface,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth()
    ) {
        Badge(
            iconResId = R.drawable.ic_scan,
            labelResId = R.string.chip_qr,
            onClick = callback::onQrClick
        )
        MarginHorizontal(12.dp)
        Badge(
            iconResId = R.drawable.ic_history_16,
            labelResId = R.string.chip_history,
            onClick = callback::onHistoryClick
        )
        Spacer(
            modifier = Modifier
                .weight(1f)
                .widthIn(min = 12.dp)
        )
        if (walletIcon != null) {
            Badge(
                icon = walletIcon,
                labelResId = R.string.chip_my_wallets,
                onClick = callback::onMyWalletsClick
            )
        }
    }
}

@Preview
@Composable
private fun CrossChainPreview() {
    val state = CrossChainSetupViewState(
        toolbarState = ToolbarViewState("Send Fund", R.drawable.ic_arrow_left_24),
        addressInputState = AddressInputState("Send to", "", ""),
        amountInputState = AmountInputViewState(
            "KSM",
            "",
            "1003 KSM",
            "$170000",
            BigDecimal("0.980"),
            "Amount",
            allowAssetChoose = true,
            initial = null
        ),
        originChainSelectorState = SelectorState("Origin network", null, null),
        destinationChainSelectorState = SelectorState("Destination network", null, null),
        originFeeInfoState = FeeInfoViewState.default,
        destinationFeeInfoState = FeeInfoViewState.default,
        warningInfoState = null,
        buttonState = ButtonViewState("Continue", true),
        walletIcon = null,
        isSoftKeyboardOpen = false,
        heightDiffDp = 0.dp
    )

    val emptyCallback = object : CrossChainSetupScreenInterface {
        override fun onNavigationClick() {}
        override fun onAddressInput(input: String) {}
        override fun onAddressInputClear() {}
        override fun onAmountInput(input: BigDecimal?) {}
        override fun onDestinationChainClick() {}
        override fun onAssetClick() {}
        override fun onNextClick() {}
        override fun onQrClick() {}
        override fun onHistoryClick() {}
        override fun onPasteClick() {}
        override fun onMyWalletsClick() {}
        override fun onAmountFocusChanged(isFocused: Boolean) {}
        override fun onQuickAmountInput(input: Double) {}
        override fun onWarningInfoClick() {}
    }

    FearlessTheme {
        CrossChainSetupContent(
            state = state,
            callback = emptyCallback
        )
    }
}
