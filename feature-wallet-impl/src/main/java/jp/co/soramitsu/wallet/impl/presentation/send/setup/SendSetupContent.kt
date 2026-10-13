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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Switch
import androidx.compose.material.SwitchColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import jp.co.soramitsu.common.compose.component.AccentDarkDisabledButton
import jp.co.soramitsu.common.compose.component.AddressInputWithScore
import jp.co.soramitsu.common.compose.component.AmountInput
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.B1
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.CapsTitle
import jp.co.soramitsu.common.compose.component.ColoredButton
import jp.co.soramitsu.common.compose.component.FeeInfo
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.QuickAmountInput
import jp.co.soramitsu.common.compose.component.QuickInput
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.compose.component.SelectorWithBorder
import jp.co.soramitsu.common.compose.component.TextInput
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.compose.component.ToolbarBottomSheet
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.WarningInfo
import jp.co.soramitsu.common.compose.component.WarningInfoState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.backgroundBlack
import jp.co.soramitsu.common.compose.theme.black05
import jp.co.soramitsu.common.compose.theme.black3
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.transparent
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white24
import jp.co.soramitsu.common.compose.theme.white64
import jp.co.soramitsu.common.utils.isZero
import jp.co.soramitsu.feature_wallet_impl.R

data class SendSetupViewState(
    val toolbarState: ToolbarViewState,
    val addressInputState: AddressInputWithScore,
    val amountInputState: AmountInputViewState,
    val chainSelectorState: SelectorState,
    val feeInfoState: FeeInfoViewState,
    val warningInfoState: WarningInfoState?,
    val buttonState: ButtonViewState,
    val isSoftKeyboardOpen: Boolean,
    val isInputLocked: Boolean,
    val quickAmountInputValues: List<QuickAmountInput> = QuickAmountInput.entries,
    val isHistoryAvailable: Boolean,
    val sendAllChecked: Boolean,
    val sendAllAllowed: Boolean,
    val commentState: TextInputViewState?
)

interface SendSetupScreenInterface {
    val onCrossChainClick: () -> Unit
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
    fun onSendAllChecked(checked: Boolean)
    fun onCommentInput(value: String)
}

@Composable
fun SendSetupContent(
    state: SendSetupViewState,
    callback: SendSetupScreenInterface
) {
    var footerHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val showQuickInput = state.amountInputState.isFocused && state.isSoftKeyboardOpen && state.isInputLocked.not()

    val focusRequester = remember { FocusRequester() }
    if (state.isInputLocked && state.amountInputState.tokenAmount.isZero()) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }

    val switchColors = object : SwitchColors {
        @Composable
        override fun thumbColor(enabled: Boolean, checked: Boolean): State<Color> {
            val color = if (enabled) {
                white
            } else {
                white64
            }
            return rememberUpdatedState(color)
        }

        @Composable
        override fun trackColor(enabled: Boolean, checked: Boolean): State<Color> {
            return rememberUpdatedState(transparent)
        }
    }

    BottomSheetScreen {
        Box(
            modifier = Modifier
                .nestedScroll(rememberNestedScrollInteropConnection())
                .fillMaxSize()
        ) {
            val bottomPadding = with(density) { footerHeight.toDp() }
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = bottomPadding)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .fillMaxWidth()
            ) {
                ToolbarBottomSheet(
                    title = stringResource(id = R.string.send_fund),
                    onNavigationClick = callback::onNavigationClick
                )
                MarginVertical(margin = 16.dp)
                AddressInputWithScore(
                    state = state.addressInputState,
                    onClear = callback::onAddressInputClear,
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
                MarginVertical(12.dp)
                state.commentState?.let {
                    TextInput(state = state.commentState, onInput = callback::onCommentInput)
                }
                state.warningInfoState?.let {
                    MarginVertical(margin = 8.dp)
                    WarningInfo(state = it, onClick = callback::onWarningInfoClick)
                }
                if (!state.isInputLocked) {
                    MarginVertical(12.dp)
                    GrayButton(
                        text = stringResource(R.string.ux_network_transfer),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = callback.onCrossChainClick
                    )
                }
                MarginVertical(margin = 8.dp)
                FeeInfo(state = state.feeInfoState, modifier = Modifier.defaultMinSize(minHeight = 52.dp))
                if (state.sendAllAllowed) {
                    B1(text = stringResource(R.string.ux_send_maximum_description))
                }

                Spacer(modifier = Modifier.weight(1f))
            }

            Column(
                modifier = Modifier
                    .onSizeChanged { footerHeight = it.height }
                    .background(backgroundBlack.copy(alpha = 0.75f))
                    .align(Alignment.BottomCenter)
            ) {
                if (state.sendAllAllowed) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .defaultMinSize(minHeight = 48.dp)
                            .toggleable(value = state.sendAllChecked, role = Role.Switch, onValueChange = callback::onSendAllChecked),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        B1(text = stringResource(id = R.string.ux_send_maximum), modifier = Modifier.weight(1f))
                        MarginHorizontal(margin = 8.dp)
                        val trackColor = when {
                            state.sendAllChecked -> colorAccentDark
                            else -> black3
                        }
                        Switch(
                            colors = switchColors,
                            checked = state.sendAllChecked,
                            onCheckedChange = null,
                            modifier = Modifier
                                .background(color = trackColor, shape = RoundedCornerShape(20.dp))
                                .padding(3.dp)
                                .height(20.dp)
                                .width(36.dp)
                                .align(Alignment.CenterVertically)
                        )
                    }
                    MarginVertical(margin = 16.dp)
                }

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
                        if (state.isHistoryAvailable) {
                            Badge(
                                modifier = Modifier.weight(1f),
                                iconResId = R.drawable.ic_history_16,
                                labelResId = R.string.chip_history,
                                onClick = callback::onHistoryClick
                            )
                        }
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
        addressInputState = AddressInputWithScore.Filled("Send to...", "0x23j2rf3bh8384j938", "", 100),
        amountInputState = AmountInputViewState(
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
        isSoftKeyboardOpen = false,
        isInputLocked = false,
        isHistoryAvailable = false,
        sendAllChecked = true,
        sendAllAllowed = true,
        commentState = TextInputViewState("Some text", "Comment")
    )

    val emptyCallback = object : SendSetupScreenInterface {
        override val onCrossChainClick: () -> Unit = {}
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
        override fun onSendAllChecked(checked: Boolean) {}
        override fun onCommentInput(value: String) {}
    }

    FearlessTheme {
        SendSetupContent(
            state = state,
            callback = emptyCallback
        )
    }
}
