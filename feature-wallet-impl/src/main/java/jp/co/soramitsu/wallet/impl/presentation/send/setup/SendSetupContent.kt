package jp.co.soramitsu.wallet.impl.presentation.send.setup

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import jp.co.soramitsu.common.compose.component.AccentDarkDisabledButton
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
import jp.co.soramitsu.common.compose.component.QuickInput
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.compose.component.SelectorWithBorder
import jp.co.soramitsu.common.compose.component.ToolbarBottomSheet
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.WarningInfo
import jp.co.soramitsu.common.compose.component.WarningInfoState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.backgroundBlack
import jp.co.soramitsu.common.compose.theme.black05
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.white24
import jp.co.soramitsu.common.utils.isZero
import jp.co.soramitsu.feature_wallet_impl.R

data class SendSetupViewState(
    val toolbarState: ToolbarViewState,
    val addressInputState: AddressInputState,
    val amountInputState: AmountInputViewState,
    val chainSelectorState: SelectorState,
    val feeInfoState: FeeInfoViewState,
    val warningInfoState: WarningInfoState?,
    val buttonState: ButtonViewState,
    val isSoftKeyboardOpen: Boolean,
    val heightDiffDp: Dp,
    val isInputLocked: Boolean,
    val quickAmountInputValues: List<QuickAmountInput> = QuickAmountInput.values().asList()
)

interface SendSetupScreenInterface {
    fun onNavigationClick()
    fun onAddressInput(input: String)
    fun onAddressInputClear()
    fun onAmountInput(input: BigDecimal?)
    fun onChainClick()
    fun onTokenClick()
    fun onNextClick()
    fun onQrClick()
    fun onHistoryClick()
    fun onPasteClick()
    fun onAmountFocusChanged(isFocused: Boolean)
    fun onQuickAmountInput(input: Double)
    fun onWarningInfoClick()
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SendSetupContent(
    state: SendSetupViewState,
    callback: SendSetupScreenInterface
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val showQuickInput = state.amountInputState.isFocused && state.isSoftKeyboardOpen && state.isInputLocked.not()

    val focusRequester = remember { FocusRequester() }
    if (state.isInputLocked && state.amountInputState.tokenAmount.isZero()) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }

    BottomSheetScreen {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = state.heightDiffDp)
        ) {
            val bottomPadding = if (state.isInputLocked) {
                50.dp
            } else {
                130.dp
            }
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = bottomPadding)
                    .fillMaxWidth()
            ) {
                ToolbarBottomSheet(
                    title = stringResource(id = R.string.send_fund),
                    onNavigationClick = callback::onNavigationClick
                )
                MarginVertical(margin = 16.dp)
                AddressInput(
                    state = state.addressInputState,
                    onInput = callback::onAddressInput,
                    onInputClear = callback::onAddressInputClear,
                    onPaste = callback::onPasteClick
                )

                MarginVertical(margin = 12.dp)
                AmountInput(
                    state = state.amountInputState,
                    borderColorFocused = colorAccentDark,
                    onInput = callback::onAmountInput,
                    onInputFocusChange = callback::onAmountFocusChanged,
                    onTokenClick = callback::onTokenClick,
                    focusRequester = focusRequester
                )

                MarginVertical(margin = 12.dp)
                SelectorWithBorder(
                    state = state.chainSelectorState,
                    onClick = callback::onChainClick
                )
                state.warningInfoState?.let {
                    MarginVertical(margin = 8.dp)
                    WarningInfo(state = it, onClick = callback::onWarningInfoClick)
                }
                MarginVertical(margin = 8.dp)
                FeeInfo(state = state.feeInfoState, modifier = Modifier.defaultMinSize(minHeight = 52.dp))

                Spacer(modifier = Modifier.weight(1f))
            }

            Column(
                modifier = Modifier
                    .background(backgroundBlack.copy(alpha = 0.75f))
                    .align(Alignment.BottomCenter)
                    .imePadding()
            ) {
                if (state.isInputLocked.not()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                    ) {
                        Badge(
                            modifier = Modifier.weight(1f),
                            iconResId = R.drawable.ic_scan,
                            labelResId = R.string.chip_qr,
                            onClick = callback::onQrClick
                        )
                        Badge(
                            modifier = Modifier.weight(1f),
                            iconResId = R.drawable.ic_history_16,
                            labelResId = R.string.chip_history,
                            onClick = callback::onHistoryClick
                        )
                    }
                    MarginVertical(margin = 12.dp)
                }
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
                if (showQuickInput && state.quickAmountInputValues.isNotEmpty()) {
                    QuickInput(
                        values = state.quickAmountInputValues.toTypedArray(),
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
private fun Badge(
    modifier: Modifier = Modifier,
    @DrawableRes iconResId: Int,
    @StringRes labelResId: Int,
    onClick: () -> Unit
) {
    ColoredButton(
        modifier = modifier,
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
            BigDecimal("0.980"),
            "Amount",
            allowAssetChoose = true,
            initial = null
        ),
        chainSelectorState = SelectorState("Network", null, null),
        feeInfoState = FeeInfoViewState.default,
        warningInfoState = null,
        buttonState = ButtonViewState("Continue", true),
        isSoftKeyboardOpen = false,
        heightDiffDp = 0.dp,
        isInputLocked = false
    )

    val emptyCallback = object : SendSetupScreenInterface {
        override fun onNavigationClick() {}
        override fun onAddressInput(input: String) {}
        override fun onAddressInputClear() {}
        override fun onAmountInput(input: BigDecimal?) {}
        override fun onChainClick() {}
        override fun onTokenClick() {}
        override fun onNextClick() {}
        override fun onQrClick() {}
        override fun onHistoryClick() {}
        override fun onPasteClick() {}
        override fun onAmountFocusChanged(isFocused: Boolean) {}
        override fun onQuickAmountInput(input: Double) {}
        override fun onWarningInfoClick() {}
    }

    FearlessTheme {
        SendSetupContent(
            state = state,
            callback = emptyCallback
        )
    }
}
