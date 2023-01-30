package jp.co.soramitsu.polkaswap.impl.presentation.swap_preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.FullScreenLoading
import jp.co.soramitsu.common.compose.component.GradientIcon
import jp.co.soramitsu.common.compose.component.Grip
import jp.co.soramitsu.common.compose.component.InfoTable
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.NavigationIconButton
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.theme.backgroundBlack
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.feature_polkaswap_impl.R
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsViewState

data class SwapPreviewState(
    val swapDetailsViewState: SwapDetailsViewState,
    val isLoading: Boolean
)

interface SwapPreviewCallbacks {

    fun onBackClick()

    fun onConfirmClick()
}

@Composable
fun SwapPreviewContent(
    state: SwapPreviewState,
    callbacks: SwapPreviewCallbacks,
    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MarginVertical(margin = 2.dp)
        Grip(Modifier.align(Alignment.CenterHorizontally))
        MarginVertical(margin = 8.dp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationIconButton(
                modifier = Modifier.padding(start = 16.dp),
                onNavigationClick = callbacks::onBackClick
            )

            Text(
                modifier = Modifier
                    .padding(
                        start = 16.dp,
                        end = 64.dp
                    )
                    .weight(1f),
                text = stringResource(R.string.polkaswap_preview_title),
                style = MaterialTheme.customTypography.header4,
                textAlign = TextAlign.Center
            )
        }
        FullScreenLoading(isLoading = state.isLoading) {
            Column {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MarginVertical(margin = 16.dp)

                    Row {
                        GradientIcon(
                            icon = state.swapDetailsViewState.fromTokenImage!!,
                            color = colorAccentDark,
                            background = backgroundBlack,
                            modifier = Modifier
                                .offset(x = 25.dp)
                                .zIndex(1f),
                            contentPadding = PaddingValues(10.dp),
                            tintImage = false
                        )
                        GradientIcon(
                            icon = state.swapDetailsViewState.toTokenImage!!,
                            color = colorAccentDark,
                            background = backgroundBlack,
                            modifier = Modifier
                                .offset(x = (-25).dp)
                                .zIndex(0f),
                            contentPadding = PaddingValues(10.dp),
                            tintImage = false
                        )
                    }

                    Text(
                        modifier = Modifier.padding(top = 16.dp),
                        text = stringResource(R.string.polkaswap_swap_title),
                        style = MaterialTheme.customTypography.header2,
                        color = MaterialTheme.customColors.white50
                    )

                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            modifier = Modifier
                                .weight(1f),
                            text = state.swapDetailsViewState.fromTokenAmount.format(),
                            style = MaterialTheme.customTypography.header3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End
                        )

                        MarginHorizontal(margin = 16.dp)

                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_right_24),
                            contentDescription = null,
                            tint = MaterialTheme.customColors.white
                        )

                        MarginHorizontal(margin = 16.dp)

                        Text(
                            modifier = Modifier.weight(1f),
                            text = state.swapDetailsViewState.toTokenAmount.format(),
                            style = MaterialTheme.customTypography.header3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start
                        )
                    }

                    val fromTokenName = state.swapDetailsViewState.fromTokenName
                    val toTokenName = state.swapDetailsViewState.toTokenName
                    InfoTable(
                        modifier = Modifier.padding(top = 24.dp),
                        items = listOf(
                            TitleValueViewState(
                                title = state.swapDetailsViewState.minmaxTitle,
                                value = state.swapDetailsViewState.toTokenMinReceived,
                                additionalValue = state.swapDetailsViewState.toFiatMinReceived
                            ),
                            TitleValueViewState(
                                title = stringResource(R.string.common_route),
                                value = "$fromTokenName  ➝  $toTokenName"
                            ),
                            TitleValueViewState(
                                title = "$fromTokenName / $toTokenName",
                                value = state.swapDetailsViewState.fromTokenOnToToken
                            ),
                            TitleValueViewState(
                                title = "$toTokenName / $fromTokenName",
                                value = state.swapDetailsViewState.toTokenOnFromToken
                            ),
                            TitleValueViewState(
                                title = stringResource(R.string.common_network_fee),
                                value = state.swapDetailsViewState.networkFee.tokenAmount,
                                additionalValue = state.swapDetailsViewState.networkFee.fiatAmount
                            )
                        )
                    )
                }

                MarginVertical(margin = 24.dp)

                AccentButton(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                    text = stringResource(R.string.common_confirm),
                    onClick = callbacks::onConfirmClick
                )

                MarginVertical(margin = 8.dp)
            }
        }
    }
}

@Preview
@Composable
fun SwapPreviewContentPreview() {
    SwapPreviewContent(
        state = SwapPreviewState(
            swapDetailsViewState = SwapDetailsViewState(
                fromTokenId = "1001",
                toTokenId = "1002",
                fromTokenName = "VAL",
                toTokenName = "XSTUSD",
                fromTokenImage = "",
                toTokenImage = "",
                toTokenMinReceived = "1",
                toFiatMinReceived = "\$0.98",
                fromTokenAmount = "1",
                toTokenAmount = "2",
                networkFee = SwapDetailsViewState.NetworkFee(
                    tokenAmount = "0.0007",
                    tokenName = "XOR",
                    fiatAmount = "\$ 0.32"
                ),
                liquidityProviderFee = SwapDetailsViewState.NetworkFee(
                    tokenAmount = "0.0007",
                    tokenName = "XOR",
                    fiatAmount = "\$ 0.32"
                ),
                fromTokenOnToToken = "0",
                toTokenOnFromToken = "0",
                minmaxTitle = stringResource(id = R.string.common_min_received)
            ),
            false
        ),
        callbacks = object : SwapPreviewCallbacks {
            override fun onBackClick() {
            }

            override fun onConfirmClick() {
            }
        }
    )
}
