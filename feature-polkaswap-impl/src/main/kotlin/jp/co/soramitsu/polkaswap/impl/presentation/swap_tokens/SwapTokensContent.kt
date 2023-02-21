package jp.co.soramitsu.polkaswap.impl.presentation.swap_tokens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.AmountInput
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.FeeInfo
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.FullScreenLoading
import jp.co.soramitsu.common.compose.component.Grip
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.NavigationIconButton
import jp.co.soramitsu.common.compose.component.Notification
import jp.co.soramitsu.common.compose.component.NotificationState
import jp.co.soramitsu.common.compose.component.QuickAmountInput
import jp.co.soramitsu.common.compose.component.QuickInput
import jp.co.soramitsu.common.compose.theme.black05
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.grayButtonBackground
import jp.co.soramitsu.common.compose.theme.warningOrange
import jp.co.soramitsu.common.compose.theme.white08
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.presentation.dataOrNull
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_polkaswap_impl.R
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsViewState

data class SwapTokensContentViewState(
    val fromAmountInputViewState: AmountInputViewState,
    val toAmountInputViewState: AmountInputViewState,
    val selectedMarket: Market,
    val swapDetailsViewState: SwapDetailsViewState?,
    val isLoading: Boolean,
    val networkFeeViewState: LoadingState<out SwapDetailsViewState.NetworkFee?>,
    val hasReadDisclaimer: Boolean
) {
    companion object {

        fun default(resourceManager: ResourceManager): SwapTokensContentViewState {
            return SwapTokensContentViewState(
                fromAmountInputViewState = AmountInputViewState.default(resourceManager, R.string.common_available_format),
                toAmountInputViewState = AmountInputViewState.default(resourceManager),
                selectedMarket = Market.SMART,
                swapDetailsViewState = null,
                isLoading = false,
                networkFeeViewState = LoadingState.Loaded(null),
                hasReadDisclaimer = false
            )
        }
    }
}

interface SwapTokensCallbacks {

    fun onChangeTokensClick()

    fun onBackClick()

    fun onPreviewClick()

    fun onFromAmountChange(amount: String)

    fun onToAmountChange(amount: String)

    fun onMarketSettingsClick()

    fun onFromTokenSelect()

    fun onToTokenSelect()

    fun onFromAmountFocusChange(focusState: FocusState)

    fun onToAmountFocusChange(focusState: FocusState)

    fun minMaxToolTopClick()

    fun liquidityProviderTooltipClick()

    fun networkFeeTooltipClick()

    fun onQuickAmountInput(value: Double)

    fun onDisclaimerClick()
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SwapTokensContent(
    state: SwapTokensContentViewState,
    callbacks: SwapTokensCallbacks,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val runCallback: (() -> Unit) -> Unit = { block ->
        keyboardController?.hide()
        block()
    }
    val isSoftKeyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    val isFromFocused = state.fromAmountInputViewState.isFocused && !state.fromAmountInputViewState.tokenName.isNullOrEmpty()
    val showQuickInput = isFromFocused && isSoftKeyboardOpen

    Column(
        modifier = modifier
            .navigationBarsPadding()
            .imePadding()
    ) {
        MarginVertical(margin = 2.dp)
        Grip(Modifier.align(Alignment.CenterHorizontally))
        MarginVertical(margin = 8.dp)

        Row(
            modifier = Modifier.padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationIconButton(
                modifier = Modifier.padding(start = 16.dp),
                onNavigationClick = callbacks::onBackClick
            )

            Image(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(
                        width = 100.dp,
                        height = 28.dp
                    ),
                painter = painterResource(R.drawable.logo_polkaswap_big),
                contentDescription = null
            )
            Spacer(modifier = Modifier.weight(1f))

            MarketLabel(
                modifier = Modifier.padding(end = 16.dp),
                market = state.selectedMarket,
                onClick = { runCallback(callbacks::onMarketSettingsClick) }
            )
        }
        FullScreenLoading(isLoading = state.isLoading) {
            Column {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            AmountInput(
                                state = state.fromAmountInputViewState,
                                borderColorFocused = colorAccentDark,
                                onInput = callbacks::onFromAmountChange,
                                onTokenClick = { runCallback(callbacks::onFromTokenSelect) },
                                onInputFocusChange = callbacks::onFromAmountFocusChange
                            )

                            MarginVertical(margin = 8.dp)

                            AmountInput(
                                state = state.toAmountInputViewState,
                                borderColorFocused = colorAccentDark,
                                onInput = callbacks::onToAmountChange,
                                onTokenClick = { runCallback(callbacks::onToTokenSelect) },
                                onInputFocusChange = callbacks::onToAmountFocusChange
                            )
                        }

                        Icon(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(grayButtonBackground)
                                .border(width = 1.dp, color = white08, shape = CircleShape)
                                .clickable { runCallback(callbacks::onChangeTokensClick) }
                                .padding(8.dp),
                            painter = painterResource(R.drawable.ic_exchange),
                            contentDescription = null,
                            tint = colorAccentDark
                        )
                    }

                    if (state.swapDetailsViewState != null) {
                        TransactionDescription(
                            swapDetailsViewState = state.swapDetailsViewState,
                            networkFeeViewState = state.networkFeeViewState,
                            callbacks = callbacks
                        )
                    }
                }
                if (state.hasReadDisclaimer.not()) {
                    Box(modifier = modifier.padding(horizontal = 16.dp)) {
                        Notification(
                            state = NotificationState(
                                iconRes = R.drawable.ic_warning_filled,
                                title = stringResource(R.string.common_disclaimer).uppercase(),
                                value = stringResource(id = R.string.polkaswap_disclaimer_message),
                                buttonText = stringResource(R.string.common_read),
                                color = warningOrange
                            ),
                            onAction = callbacks::onDisclaimerClick
                        )
                    }
                    MarginVertical(margin = 16.dp)
                }
                AccentButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp, top = 16.dp),
                    text = stringResource(R.string.common_preview),
                    enabled = state.swapDetailsViewState != null,
                    onClick = { runCallback(callbacks::onPreviewClick) }
                )

                if (showQuickInput) {
                    QuickInput(
                        values = QuickAmountInput.values(),
                        onQuickAmountInput = {
                            keyboardController?.hide()
                            callbacks.onQuickAmountInput(it)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionDescription(
    swapDetailsViewState: SwapDetailsViewState,
    networkFeeViewState: LoadingState<out SwapDetailsViewState.NetworkFee?>,
    modifier: Modifier = Modifier,
    callbacks: SwapTokensCallbacks
) {
    Column(
        modifier = modifier
    ) {
        FeeInfo(
            modifier = Modifier.padding(top = 8.dp),
            state = FeeInfoViewState(
                caption = swapDetailsViewState.minmaxTitle,
                feeAmount = swapDetailsViewState.toTokenMinReceived,
                feeAmountFiat = swapDetailsViewState.toFiatMinReceived,
                tooltip = true
            ),
            tooltipClick = callbacks::minMaxToolTopClick
        )

        FeeInfo(
            state = FeeInfoViewState(
                caption = stringResource(R.string.common_route),
                feeAmount = "${swapDetailsViewState.fromTokenName}  ➝  ${swapDetailsViewState.toTokenName}",
                feeAmountFiat = null
            )
        )

        FeeInfo(
            state = FeeInfoViewState(
                caption = "${swapDetailsViewState.fromTokenName} / ${swapDetailsViewState.toTokenName}",
                feeAmount = swapDetailsViewState.fromTokenOnToToken.format(),
                feeAmountFiat = null
            )
        )

        FeeInfo(
            state = FeeInfoViewState(
                caption = "${swapDetailsViewState.toTokenName} / ${swapDetailsViewState.fromTokenName}",
                feeAmount = swapDetailsViewState.toTokenOnFromToken.format(),
                feeAmountFiat = null
            )
        )

        FeeInfo(
            state = FeeInfoViewState(
                caption = stringResource(R.string.common_liquidity_provider_fee),
                feeAmount = swapDetailsViewState.liquidityProviderFee.tokenAmount,
                feeAmountFiat = swapDetailsViewState.liquidityProviderFee.fiatAmount,
                tooltip = true
            ),
            tooltipClick = callbacks::liquidityProviderTooltipClick
        )

        when {
            networkFeeViewState is LoadingState.Loading -> FeeInfo(
                state = FeeInfoViewState(
                    caption = stringResource(R.string.common_network_fee),
                    feeAmount = null,
                    feeAmountFiat = null,
                    tooltip = true
                ),
                tooltipClick = callbacks::networkFeeTooltipClick
            )
            networkFeeViewState is LoadingState.Loaded && networkFeeViewState.dataOrNull() != null -> FeeInfo(
                state = FeeInfoViewState(
                    caption = stringResource(R.string.common_network_fee),
                    feeAmount = networkFeeViewState.dataOrNull()?.tokenAmount,
                    feeAmountFiat = networkFeeViewState.dataOrNull()?.fiatAmount,
                    tooltip = true
                ),
                tooltipClick = callbacks::networkFeeTooltipClick
            )
        }
    }
}

@Composable
private fun MarketLabel(
    market: Market,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(black05)
            .clickable { onClick.invoke() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.padding(
                start = 8.dp,
                top = 4.dp,
                bottom = 4.dp,
                end = 4.dp
            ),
            text = stringResource(R.string.polkaswap_market),
            style = MaterialTheme.customTypography.body1
        )

        Text(
            text = market.marketName,
            style = MaterialTheme.customTypography.header5
        )

        Icon(
            modifier = Modifier
                .padding(start = 4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.customColors.white08)
                .size(32.dp)
                .padding(4.dp),
            painter = painterResource(R.drawable.ic_settings),
            contentDescription = null,
            tint = MaterialTheme.customColors.white
        )
    }
}
