package jp.co.soramitsu.wallet.impl.presentation.send.setup

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.AddressInput
import jp.co.soramitsu.common.compose.component.AddressInputState
import jp.co.soramitsu.common.compose.component.AmountInput
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.CapsTitle
import jp.co.soramitsu.common.compose.component.ColoredButton
import jp.co.soramitsu.common.compose.component.FeeInfo
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.QuickAmountInput
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.compose.component.SelectorWithBorder
import jp.co.soramitsu.common.compose.component.ToolbarBottomSheet
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black05
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.white24
import jp.co.soramitsu.feature_wallet_impl.R

data class SendSetupViewState(
    val toolbarState: ToolbarViewState,
    val addressInputState: AddressInputState,
    val amountInputState: AmountInputViewState,
    val chainSelectorState: SelectorState,
    val feeInfoState: FeeInfoViewState,
    val buttonState: ButtonViewState
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SendSetupContent(
    state: SendSetupViewState,
    onNavigationClick: () -> Unit = {},
    onAddressInput: (String) -> Unit = {},
    onAddressInputClear: () -> Unit = {},
    onAmountInput: (String) -> Unit = {},
    onChainClick: () -> Unit = {},
    onTokenClick: () -> Unit = {},
    onNextClick: () -> Unit = {},
    onScanClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onPasteClick: () -> Unit = {},
    onAmountFocusChanged: (FocusState) -> Unit = {},
    onQuickAmountInput: (amount: Double) -> Unit = {}
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    BottomSheetScreen {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            ) {
                ToolbarBottomSheet(
                    title = stringResource(id = R.string.send_fund),
                    onNavigationClicked = { onNavigationClick() }
                )
                MarginVertical(margin = 20.dp)
                AddressInput(
                    state = state.addressInputState,
                    onInput = onAddressInput,
                    onInputClear = onAddressInputClear
                )

                MarginVertical(margin = 12.dp)
                AmountInput(
                    state = state.amountInputState,
                    borderColorFocused = colorAccentDark,
                    onInput = onAmountInput,
                    onInputFocusChange = onAmountFocusChanged,
                    onTokenClick = onTokenClick
                )

                MarginVertical(margin = 12.dp)
                SelectorWithBorder(
                    state = state.chainSelectorState,
                    onClick = onChainClick
                )
                MarginVertical(margin = 8.dp)
                FeeInfo(state = state.feeInfoState)

                Spacer(modifier = Modifier.weight(1f))
                Row {
                    Badge(R.drawable.ic_scan, R.string.chip_scan, onScanClick)
                    MarginHorizontal(margin = 12.dp)
                    Badge(R.drawable.ic_history_16, R.string.chip_history, onHistoryClick)
                    MarginHorizontal(margin = 12.dp)
                    Badge(R.drawable.ic_copy_16, R.string.chip_paste, onPasteClick)
                }
                MarginVertical(margin = 12.dp)
                AccentButton(
                    state = state.buttonState,
                    onClick = {
                        keyboardController?.hide()
                        onNextClick()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                )

                MarginVertical(margin = 12.dp)
            }

            val isSoftKeyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            val showQuickInput = state.amountInputState.isFocused && isSoftKeyboardOpen
            if (showQuickInput) {
                QuickAmountInput(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .imePadding(),
                    onQuickAmountInput = {
                        keyboardController?.hide()
                        onQuickAmountInput(it)
                    }
                )
            }
        }
    }
}

@Composable
private fun Badge(
    @DrawableRes iconResId: Int,
    @StringRes labelResId: Int,
    onClick: () -> Unit
) {
    ColoredButton(
        backgroundColor = black05,
        border = BorderStroke(1.dp, white24),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        onClick = onClick
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            tint = Color.White,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        MarginHorizontal(margin = 4.dp)
        CapsTitle(text = stringResource(id = labelResId))
    }
}

@Preview
@Composable
private fun SendSetupPreview() {
    val state = SendSetupViewState(
        toolbarState = ToolbarViewState("Send Fund", R.drawable.ic_arrow_left_24),
        addressInputState = AddressInputState("Send to", "", ""),
        amountInputState = AmountInputViewState(
            "KSM",
            "",
            "1003 KSM",
            "$170000",
            "0,980",
            "Amount",
            allowAssetChoose = true
        ),
        chainSelectorState = SelectorState("Network", null, null),
        feeInfoState = FeeInfoViewState.default,
        buttonState = ButtonViewState("Continue", true)
    )

    FearlessTheme {
        SendSetupContent(
            state = state
        )
    }
}
