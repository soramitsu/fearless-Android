package jp.co.soramitsu.polkaswap.impl.presentation.transaction_settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.Grip
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.NavigationIconButton
import jp.co.soramitsu.common.compose.component.NumberInput
import jp.co.soramitsu.common.compose.component.NumberInputState
import jp.co.soramitsu.common.compose.component.QuickInput
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.compose.component.SelectorWithBorder
import jp.co.soramitsu.common.compose.component.Slider
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.warningOrange
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_polkaswap_impl.R
import jp.co.soramitsu.polkaswap.impl.domain.models.Market

data class TransactionSettingsViewState(
    val marketState: SelectorState,
    val slippageInputState: NumberInputState
) {
    companion object {
        fun default(resourceManager: ResourceManager): TransactionSettingsViewState {
            return TransactionSettingsViewState(
                marketState = SelectorState(
                    title = resourceManager.getString(R.string.polkaswap_market_title),
                    subTitle = Market.SMART.marketName,
                    iconUrl = null
                ),
                slippageInputState = NumberInputState(
                    title = resourceManager.getString(R.string.polkaswap_slippage_tolerance),
                    value = "0.5",
                    suffix = "%",
                    decimalPlaces = 1
                )
            )
        }
    }
}

private enum class SlippageToleranceQuickInput(
    override val label: String,
    override val value: Double
) : QuickInput {
    SMALL("0.1%", 0.1),
    MEDIUM("0.5%", 0.5),
    BIG("1%", 1.0)
}

interface TransactionSettingsCallbacks {

    fun onMarketClick()

    fun onCloseClick()

    fun onResetToDefaultClick()

    fun onSaveClick()

    fun onQuickSlippageInput(value: Double)

    fun onSlippageValueChange(value: String)

    fun onSlippageValueChange(value: Float)

    fun onAmountFocusChanged(focusState: FocusState)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TransactionSettingsContent(
    state: TransactionSettingsViewState,
    callbacks: TransactionSettingsCallbacks,
    modifier: Modifier = Modifier
) {
    val isSoftKeyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val keyboardController = LocalSoftwareKeyboardController.current
    Column(
        modifier = modifier
            .imePadding()
            .navigationBarsPadding()
            .fillMaxHeight()
    ) {
        MarginVertical(margin = 2.dp)
        Grip(Modifier.align(Alignment.CenterHorizontally))
        MarginVertical(margin = 8.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 64.dp, end = 16.dp),
                text = stringResource(R.string.polkaswap_swap_settings_title),
                style = MaterialTheme.customTypography.header4,
                textAlign = TextAlign.Center
            )
            NavigationIconButton(
                navigationIconResId = R.drawable.ic_close,
                onNavigationClick = callbacks::onCloseClick
            )
        }

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            SelectorWithBorder(
                modifier = Modifier.padding(top = 16.dp),
                state = state.marketState,
                onClick = callbacks::onMarketClick
            )

            NumberInput(
                modifier = Modifier.padding(top = 12.dp),
                state = state.slippageInputState,
                onValueChange = callbacks::onSlippageValueChange,
                onInputFocusChange = callbacks::onAmountFocusChanged
            )

            Row {
                Text(
                    text = stringResource(R.string.polkaswap_transaction_may_fail),
                    style = MaterialTheme.customTypography.body2,
                    color = warningOrange
                )
            }

            val slippageStateValue = state.slippageInputState.value

            val sliderPosition by remember(slippageStateValue) {
                derivedStateOf { slippageStateValue.toFloat() }
            }
            Slider(
                modifier = Modifier.padding(top = 2.dp),
                value = sliderPosition,
                onValueChange = callbacks::onSlippageValueChange,
                valueRange = TransactionSettingsViewModel.SlippageRange,
                step = 0.1f
            )

            Text(
                modifier = Modifier
                    .alpha(0.5f)
                    .padding(horizontal = 16.dp),
                text = stringResource(R.string.polkaswap_slippage_tolerance_description),
                style = MaterialTheme.customTypography.body2,
                textAlign = TextAlign.Center
            )

            MarginVertical(margin = 124.dp)
        }

        GrayButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            text = stringResource(R.string.polkaswap_btn_reset_to_default),
            onClick = callbacks::onResetToDefaultClick
        )
        MarginVertical(margin = 8.dp)
        AccentButton(
            text = stringResource(R.string.polkaswap_btn_save),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .fillMaxWidth(),
            onClick = callbacks::onSaveClick
        )

        val showQuickInput = state.slippageInputState.isFocused && isSoftKeyboardOpen
        if (showQuickInput) {
            QuickInput(
                values = SlippageToleranceQuickInput.values(),
                onQuickAmountInput = {
                    keyboardController?.hide()
                    callbacks.onQuickSlippageInput(it)
                }
            )
        }
    }
}

@Preview
@Composable
fun TransactionSettingsContentPreview() {
    TransactionSettingsContent(
        state = TransactionSettingsViewState(
            marketState = SelectorState(
                title = "Market",
                subTitle = "SMART",
                iconUrl = null
            ),
            slippageInputState = NumberInputState(
                title = "Slippage Tolerance",
                value = "0.5",
                suffix = "%",
                warning = false,
                isFocused = false
            )
        ),
        callbacks = object : TransactionSettingsCallbacks {
            override fun onMarketClick() {
            }

            override fun onCloseClick() {
            }

            override fun onResetToDefaultClick() {
            }

            override fun onSaveClick() {
            }

            override fun onQuickSlippageInput(value: Double) {
            }

            override fun onSlippageValueChange(value: String) {
            }

            override fun onSlippageValueChange(value: Float) {
            }

            override fun onAmountFocusChanged(focusState: FocusState) {
            }
        }
    )
}
