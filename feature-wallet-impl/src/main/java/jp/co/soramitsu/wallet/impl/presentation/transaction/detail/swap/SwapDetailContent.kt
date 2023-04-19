package jp.co.soramitsu.wallet.impl.presentation.transaction.detail.swap

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.GradientIconState
import jp.co.soramitsu.common.compose.component.InfoTable
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.SwapHeader
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.ToolbarBottomSheet
import jp.co.soramitsu.common.utils.formatDateTime
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.polkaswap.api.models.Market

data class SwapDetailState(
    val fromTokenImage: GradientIconState,
    val toTokenImage: GradientIconState,
    val fromTokenAmount: String,
    val toTokenAmount: String,
    val fromTokenName: String,
    val toTokenName: String,
    val statusAppearance: SwapStatusAppearance,
    val address: String,
    val fromTokenOnToToken: String,
    val liquidityProviderFee: String,
    val networkFee: String,
    val time: Long,
    val market: Market
)

interface SwapDetailCallbacks {
    fun onBackClick()

    fun onCloseClick()
}

@Composable
fun SwapPreviewContent(
    state: SwapDetailState,
    callbacks: SwapDetailCallbacks,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ToolbarBottomSheet(
            modifier = Modifier.padding(horizontal = 16.dp),
            title = stringResource(R.string.polkaswap_preview_title),
            onNavigationClick = callbacks::onBackClick
        )
        Column {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MarginVertical(margin = 16.dp)

                SwapHeader(
                    fromTokenImage = state.fromTokenImage,
                    toTokenImage = state.toTokenImage,
                    fromTokenAmount = state.fromTokenAmount,
                    toTokenAmount = state.toTokenAmount
                )

                val infoItems = listOf(
                    TitleValueViewState(
                        title = stringResource(R.string.transaction_detail_status),
                        value = stringResource(state.statusAppearance.labelRes),
                        valueColor = state.statusAppearance.color
                    ),
                    TitleValueViewState(
                        title = stringResource(R.string.common_account),
                        value = state.address
                    ),
                    TitleValueViewState(
                        title = stringResource(R.string.common_date),
                        value = state.time.formatDateTime(LocalContext.current).toString()
                    ),
                    TitleValueViewState(stringResource(R.string.common_route), "${state.fromTokenName}  ➝  ${state.toTokenName}"),
                    TitleValueViewState("${state.fromTokenName} / ${state.toTokenName}", state.fromTokenOnToToken),
                    TitleValueViewState(
                        title = stringResource(R.string.common_liquidity_provider_fee),
                        value = state.liquidityProviderFee,
                        additionalValue = null
                    ),
                    TitleValueViewState(
                        title = stringResource(R.string.common_network_fee),
                        value = state.networkFee,
                        additionalValue = null
                    ),
                    TitleValueViewState(
                        title = stringResource(R.string.polkaswap_market_title),
                        value = state.market.marketName
                    )
                )
                InfoTable(
                    modifier = Modifier.padding(top = 24.dp),
                    items = infoItems
                )
            }

            MarginVertical(margin = 24.dp)

            AccentButton(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                text = stringResource(R.string.common_close),
                onClick = callbacks::onCloseClick
            )

            MarginVertical(margin = 8.dp)
        }
    }
}

@Preview
@Composable
fun SwapDetailContentPreview() {
    SwapPreviewContent(
        state = SwapDetailState(
            fromTokenName = "VAL",
            toTokenName = "XSTUSD",
            fromTokenImage = GradientIconState.Remote("", ""),
            toTokenImage = GradientIconState.Remote("", ""),
            fromTokenAmount = "1",
            toTokenAmount = "2",
            liquidityProviderFee = "0.0007",
            fromTokenOnToToken = "0",
            networkFee = "0.0007",
            address = "asdfqwaefgqwef2fr",
            statusAppearance = SwapStatusAppearance.COMPLETED,
            time = 1675834923575L,
            market = Market.SMART
        ),
        callbacks = object : SwapDetailCallbacks {
            override fun onBackClick() {}
            override fun onCloseClick() {}
        }
    )
}
