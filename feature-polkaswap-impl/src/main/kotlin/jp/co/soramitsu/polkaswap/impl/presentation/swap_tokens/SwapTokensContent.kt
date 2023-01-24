package jp.co.soramitsu.polkaswap.impl.presentation.swap_tokens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.AmountInput
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.FeeInfo
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.Grip
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.NavigationIconButton
import jp.co.soramitsu.common.compose.theme.black05
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.grayButtonBackground
import jp.co.soramitsu.common.compose.theme.white08
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.feature_polkaswap_impl.R
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetails
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount

data class SwapTokensContentViewState(
    val fromAmountInputViewState: AmountInputViewState,
    val toAmountInputViewState: AmountInputViewState,
    val selectedMarket: Market,
    val swapDetails: SwapDetails?
) {
    companion object {

        fun default(resourceManager: ResourceManager): SwapTokensContentViewState {
            return SwapTokensContentViewState(
                fromAmountInputViewState = AmountInputViewState.default(resourceManager),
                toAmountInputViewState = AmountInputViewState.default(resourceManager),
                selectedMarket = Market.SMART,
                swapDetails = null
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
}

@Composable
fun SwapTokensContent(
    state: SwapTokensContentViewState,
    callbacks: SwapTokensCallbacks,
    modifier: Modifier = Modifier
) {
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
                onClick = callbacks::onMarketSettingsClick
            )
        }

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
                        onTokenClick = callbacks::onFromTokenSelect,
                        onInputFocusChange = callbacks::onFromAmountFocusChange
                    )

                    MarginVertical(margin = 8.dp)

                    AmountInput(
                        state = state.toAmountInputViewState,
                        borderColorFocused = colorAccentDark,
                        onInput = callbacks::onToAmountChange,
                        onTokenClick = callbacks::onToTokenSelect,
                        onInputFocusChange = callbacks::onToAmountFocusChange
                    )
                }

                Icon(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(grayButtonBackground)
                        .border(width = 1.dp, color = white08, shape = CircleShape)
                        .clickable { callbacks.onChangeTokensClick() }
                        .padding(8.dp),
                    painter = painterResource(R.drawable.ic_exchange),
                    contentDescription = null,
                    tint = colorAccentDark
                )
            }

            if (state.swapDetails != null) {
                TransactionDescription(state.swapDetails)
            }
        }

        AccentButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp, top = 16.dp),
            text = stringResource(R.string.common_continue),
            enabled = state.swapDetails != null,
            onClick = callbacks::onPreviewClick
        )
    }
}

@Composable
private fun TransactionDescription(
    swapDetails: SwapDetails,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        FeeInfo(
            modifier = Modifier.padding(top = 8.dp),
            state = FeeInfoViewState(
                caption = stringResource(R.string.common_min_received),
                feeAmount = swapDetails.toTokenMinReceived.formatTokenAmount(swapDetails.toTokenName),
                feeAmountFiat = swapDetails.toFiatMinReceived
            )
        )

        FeeInfo(
            state = FeeInfoViewState(
                caption = stringResource(R.string.common_route),
                feeAmount = "${swapDetails.fromTokenName}  ➝  ${swapDetails.toTokenName}",
                feeAmountFiat = null
            )
        )

        FeeInfo(
            state = FeeInfoViewState(
                caption = "${swapDetails.fromTokenName} / ${swapDetails.toTokenName}",
                feeAmount = swapDetails.fromTokenOnToToken.format(),
                feeAmountFiat = null
            )
        )

        FeeInfo(
            state = FeeInfoViewState(
                caption = "${swapDetails.toTokenName} / ${swapDetails.fromTokenName}",
                feeAmount = swapDetails.toTokenOnFromToken.format(),
                feeAmountFiat = null
            )
        )

        FeeInfo(
            state = FeeInfoViewState(
                caption = stringResource(R.string.common_liquidity_provider_fee),
                feeAmount = "0",
                feeAmountFiat = "$0"
            )
        )

        FeeInfo(
            state = FeeInfoViewState(
                caption = stringResource(R.string.common_network_fee),
                feeAmount = "0",
                feeAmountFiat = "$0"
            )
        )
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
