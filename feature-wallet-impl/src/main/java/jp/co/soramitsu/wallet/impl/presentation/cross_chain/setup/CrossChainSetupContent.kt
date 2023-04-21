package jp.co.soramitsu.wallet.impl.presentation.cross_chain.setup

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
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
import jp.co.soramitsu.common.compose.component.QuickInput
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.compose.component.SelectorWithBorder
import jp.co.soramitsu.common.compose.component.ToolbarBottomSheet
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.WarningInfo
import jp.co.soramitsu.common.compose.component.WarningInfoState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black05
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.white24
import jp.co.soramitsu.feature_wallet_impl.R
import java.math.BigDecimal

data class CrossChainSetupViewState(
    val toolbarState: ToolbarViewState,
    val addressInputState: AddressInputState,
    val amountInputState: AmountInputViewState,
    val originalChainSelectorState: SelectorState,
    val destinationChainSelectorState: SelectorState,
    val originalFeeInfoState: FeeInfoViewState,
    val destinationFeeInfoState: FeeInfoViewState?,
    val warningInfoState: WarningInfoState?,
    val buttonState: ButtonViewState
)

interface CrossChainSetupScreenInterface {
    fun onNavigationClick()
    fun onAddressInput(input: String)
    fun onAddressInputClear()
    fun onAmountInput(input: BigDecimal?)
    fun onOriginalChainClick()
    fun onDestinationChainClick()
    fun onTokenClick()
    fun onNextClick()
    fun onQrClick()
    fun onHistoryClick()
    fun onPasteClick()
    fun onAmountFocusChanged(focusState: FocusState)
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

    BottomSheetScreen {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            ) {
                ToolbarBottomSheet(
                    title = stringResource(id = R.string.common_title_cross_chain),
                    onNavigationClick = callback::onNavigationClick
                )

                MarginVertical(margin = 16.dp)
                SelectorWithBorder(
                    state = state.originalChainSelectorState,
                    onClick = callback::onOriginalChainClick
                )

                MarginVertical(margin = 8.dp)
                AmountInput(
                    state = state.amountInputState,
                    borderColorFocused = colorAccentDark,
                    onTokenClick = callback::onTokenClick
                )

                MarginVertical(margin = 8.dp)
                SelectorWithBorder(
                    state = state.destinationChainSelectorState,
                    onClick = callback::onDestinationChainClick
                )

                MarginVertical(margin = 8.dp)
                AddressInput(
                    state = state.addressInputState,
                    onInput = callback::onAddressInput,
                    onInputClear = callback::onAddressInputClear
                )
                MarginVertical(margin = 8.dp)
                AddressActions(callback = callback)

                state.warningInfoState?.let {
                    MarginVertical(margin = 8.dp)
                    WarningInfo(state = it, onClick = callback::onWarningInfoClick)
                }
                MarginVertical(margin = 8.dp)
                FeeInfo(state = state.originalFeeInfoState, modifier = Modifier.defaultMinSize(minHeight = 52.dp))
                if (state.destinationFeeInfoState != null) {
                    MarginVertical(margin = 8.dp)
                    FeeInfo(state = state.destinationFeeInfoState, modifier = Modifier.defaultMinSize(minHeight = 52.dp))
                }

                Spacer(modifier = Modifier.weight(1f))
            }

            val isSoftKeyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            val showQuickInput = state.amountInputState.isFocused && isSoftKeyboardOpen
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
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
    callback: CrossChainSetupScreenInterface,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth()
    ) {
        Badge(
            modifier = Modifier.weight(1f),
            iconResId = R.drawable.ic_scan,
            labelResId = R.string.chip_qr,
            onClick = callback::onQrClick
        )
        MarginHorizontal(12.dp)
        Badge(
            modifier = Modifier.weight(1f),
            iconResId = R.drawable.ic_history_16,
            labelResId = R.string.chip_history,
            onClick = callback::onHistoryClick
        )
        MarginHorizontal(12.dp)
        Badge(
            modifier = Modifier.weight(1f),
            iconResId = R.drawable.ic_copy_16,
            labelResId = R.string.chip_paste,
            onClick = callback::onPasteClick
        )
    }
}

@Composable
private fun Badge(
    @DrawableRes iconResId: Int,
    @StringRes labelResId: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
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
        originalChainSelectorState = SelectorState("Original network", null, null),
        destinationChainSelectorState = SelectorState("Destination network", null, null),
        originalFeeInfoState = FeeInfoViewState.default,
        destinationFeeInfoState = FeeInfoViewState.default,
        warningInfoState = null,
        buttonState = ButtonViewState("Continue", true)
    )

    val emptyCallback = object : CrossChainSetupScreenInterface {
        override fun onNavigationClick() {}
        override fun onAddressInput(input: String) {}
        override fun onAddressInputClear() {}
        override fun onAmountInput(input: BigDecimal?) {}
        override fun onOriginalChainClick() {}
        override fun onDestinationChainClick() {}
        override fun onTokenClick() {}
        override fun onNextClick() {}
        override fun onQrClick() {}
        override fun onHistoryClick() {}
        override fun onPasteClick() {}
        override fun onAmountFocusChanged(focusState: FocusState) {}
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
