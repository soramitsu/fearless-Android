package jp.co.soramitsu.wallet.impl.presentation.send.setupcbdc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import jp.co.soramitsu.common.compose.component.AccentDarkDisabledButton
import jp.co.soramitsu.common.compose.component.AddressInput
import jp.co.soramitsu.common.compose.component.AddressInputState
import jp.co.soramitsu.common.compose.component.AmountInput
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.FeeInfo
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.compose.component.SelectorWithBorder
import jp.co.soramitsu.common.compose.component.ToolbarBottomSheet
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.WarningInfo
import jp.co.soramitsu.common.compose.component.WarningInfoState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.backgroundBlack
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.utils.isZero
import jp.co.soramitsu.feature_wallet_impl.R

data class CBDCSendSetupViewState(
    val toolbarState: ToolbarViewState,
    val addressInputState: AddressInputState,
    val amountInputState: AmountInputViewState,
    val chainSelectorState: SelectorState,
    val feeInfoState: FeeInfoViewState,
    val warningInfoState: WarningInfoState?,
    val buttonState: ButtonViewState,
    val isSoftKeyboardOpen: Boolean
)

interface CBDCSendSetupScreenInterface {
    fun onNavigationClick()
    fun onAmountInput(input: BigDecimal?)
    fun onNextClick()
    fun onAmountFocusChanged(isFocused: Boolean)
    fun onWarningInfoClick()
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CBCDSendSetupContent(
    state: CBDCSendSetupViewState,
    callback: CBDCSendSetupScreenInterface
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    val focusRequester = remember { FocusRequester() }
    if (state.amountInputState.tokenAmount.isZero()) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }

    BottomSheetScreen {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp)
                .weight(1f)
        ) {
            ToolbarBottomSheet(
                title = stringResource(id = R.string.send_fund),
                onNavigationClick = callback::onNavigationClick
            )
            MarginVertical(margin = 16.dp)
            AddressInput(
                state = state.addressInputState
            )

            MarginVertical(margin = 12.dp)
            AmountInput(
                state = state.amountInputState,
                borderColorFocused = colorAccentDark,
                onInput = callback::onAmountInput,
                onInputFocusChange = callback::onAmountFocusChanged,
                focusRequester = focusRequester
            )

            MarginVertical(margin = 12.dp)
            SelectorWithBorder(
                state = state.chainSelectorState
            )
            state.warningInfoState?.let {
                MarginVertical(margin = 8.dp)
                WarningInfo(state = it, onClick = callback::onWarningInfoClick)
            }
            MarginVertical(margin = 8.dp)
            FeeInfo(state = state.feeInfoState, modifier = Modifier.defaultMinSize(minHeight = 52.dp))
        }

        Column(
            modifier = Modifier
                .background(backgroundBlack.copy(alpha = 0.75f))
        ) {
            AccentDarkDisabledButton(
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
        }
    }
}

@Preview
@Composable
private fun CBDCSendSetupPreview() {
    val state = CBDCSendSetupViewState(
        toolbarState = ToolbarViewState("Send Fund", R.drawable.ic_arrow_left_24),
        addressInputState = AddressInputState("Send to", "", ""),
        amountInputState = AmountInputViewState(
            "CBDC chain",
            "KSM",
            "",
            "1003 KSM",
            "$170000",
            BigDecimal("0.980"),
            "Amount",
            allowAssetChoose = true
        ),
        chainSelectorState = SelectorState("Network", null, null),
        feeInfoState = FeeInfoViewState.default,
        warningInfoState = null,
        buttonState = ButtonViewState("Continue", true),
        isSoftKeyboardOpen = false
    )

    val emptyCallback = object : CBDCSendSetupScreenInterface {
        override fun onNavigationClick() {}
        override fun onAmountInput(input: BigDecimal?) {}
        override fun onNextClick() {}
        override fun onAmountFocusChanged(isFocused: Boolean) {}
        override fun onWarningInfoClick() {}
    }

    FearlessTheme {
        CBCDSendSetupContent(
            state = state,
            callback = emptyCallback
        )
    }
}
