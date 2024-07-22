package jp.co.soramitsu.liquiditypools.impl.presentation.pooldetails

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.DoubleGradientIcon
import jp.co.soramitsu.common.compose.component.GradientIconState
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.H4Bold
import jp.co.soramitsu.common.compose.component.InfoTable
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.NavigationIconButton
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.confirm.GradientIconData

data class PoolDetailsState(
    val originTokenIcon: GradientIconData? = null,
    val destinationTokenIcon: GradientIconData? = null,
    val fromTokenSymbol: String? = null,
    val toTokenSymbol: String? = null,
    val tvl: String? = null,
    val apy: String? = null
)

interface PoolDetailsCallbacks {

    fun onSupplyLiquidityClick()
    fun onRemoveLiquidityClick()
}

@Composable
fun PoolDetailsScreen(
    state: PoolDetailsState,
    callbacks: PoolDetailsCallbacks
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MarginVertical(margin = 16.dp)

            DoubleGradientIcon(
                leftImage = provideGradientIconState(state.originTokenIcon),
                rightImage = provideGradientIconState(state.destinationTokenIcon)
            )

            Row(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = state.fromTokenSymbol.orEmpty(),
                    style = MaterialTheme.customTypography.header3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End
                )

                MarginHorizontal(margin = 8.dp)

                Icon(
                    painter = painterResource(jp.co.soramitsu.common.R.drawable.ic_arrow_right_24),
                    contentDescription = null,
                    tint = MaterialTheme.customColors.white
                )

                MarginHorizontal(margin = 8.dp)

                Text(
                    text = state.toTokenSymbol.orEmpty(),
                    style = MaterialTheme.customTypography.header3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start
                )
            }

            InfoTable(
                modifier = Modifier.padding(top = 24.dp),
                items = listOf(
                    TitleValueViewState(
                        title = "TVL",
                        value = state.tvl
                    ),
                    TitleValueViewState(
                        title = "Strategic bonus APY",
                        value = state.apy,
                        clickState = TitleValueViewState.ClickState.Title(R.drawable.ic_info_14, 1)
                    ),
                    TitleValueViewState(
                        title = "Rewards payout in",
                        value = "pswap"
                    )
                )
            )

            MarginVertical(margin = 24.dp)

            AccentButton(
                modifier = Modifier
                    .height(48.dp)
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                text = "Supply liquidity",
                onClick = callbacks::onSupplyLiquidityClick
            )

            MarginVertical(margin = 8.dp)
            GrayButton(
                modifier = Modifier
                    .height(48.dp)
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                text = "Remove liquidity",
                onClick = callbacks::onRemoveLiquidityClick
            )

            MarginVertical(margin = 8.dp)
        }
    }
}

private fun provideGradientIconState(gradientIconData: GradientIconData?): GradientIconState {
    val url = gradientIconData?.url
    return if (url == null) {
        GradientIconState.Local(
            res = R.drawable.ic_fearless_logo
        )
    } else {
        GradientIconState.Remote(
            url = url,
            color = gradientIconData.color
        )
    }
}


@Preview
@Composable
private fun PreviewPoolDetailsScreen() {
    BottomSheetScreen {
        PoolDetailsScreen(
            state = PoolDetailsState(
                originTokenIcon = GradientIconData(null, null),
                destinationTokenIcon = GradientIconData(null, null),
                fromTokenSymbol = "XOR",
                toTokenSymbol = "ETH",
                apy = "23.3%",
                tvl = "$34.999 TVL",
            ),
            callbacks = object : PoolDetailsCallbacks {
                override fun onSupplyLiquidityClick() {}
                override fun onRemoveLiquidityClick() {}
            },
        )
    }
}
