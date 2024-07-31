package jp.co.soramitsu.liquiditypools.impl.presentation.pooldetails

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.BackgroundCorneredWithBorder
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.InfoTableItem
import jp.co.soramitsu.common.compose.component.InfoTableItemAsset
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TitleIconValueState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.getImageRequest
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.liquiditypools.impl.presentation.PoolsFlowViewModel
import jp.co.soramitsu.liquiditypools.impl.presentation.PoolsFlowViewModel.Companion.ITEM_APY_ID

data class PoolDetailsState(
    val assetFrom: Asset? = null,
    val assetTo: Asset? = null,
    val pooledBaseAmount: String = "",
    val pooledBaseFiat: String = "",
    val pooledTargetAmount: String = "",
    val pooledTargetFiat: String = "",
    val tvl: String? = null,
    val apy: String? = null
)

interface PoolDetailsCallbacks {
    fun onSupplyLiquidityClick()
    fun onRemoveLiquidityClick()
    fun onDetailItemClick(itemId: Int)
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

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                AsyncImage(
                    model = getImageRequest(LocalContext.current, state.assetFrom?.iconUrl.orEmpty()),
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .offset(x = 14.dp)
                        .zIndex(1f)
                )
                AsyncImage(
                    model = getImageRequest(LocalContext.current, state.assetTo?.iconUrl.orEmpty()),
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .offset(x = (-14).dp)
                        .zIndex(0f)
                )
            }

            Row(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = state.assetFrom?.symbol?.uppercase().orEmpty(),
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
                    text = state.assetTo?.symbol?.uppercase().orEmpty(),
                    style = MaterialTheme.customTypography.header3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start
                )
            }

            MarginVertical(margin = 24.dp)
            BackgroundCorneredWithBorder(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Column {
                    InfoTableItem(TitleValueViewState("TVL", state.tvl))
                    InfoTableItem(
                        TitleValueViewState(
                            title = "Strategic bonus APY",
                            value = state.apy,
                            clickState = TitleValueViewState.ClickState.Title(R.drawable.ic_info_14, ITEM_APY_ID)
                        ),
                        onClick = { callbacks.onDetailItemClick(ITEM_APY_ID) }
                    )
                    InfoTableItemAsset(
                        TitleIconValueState(
                            title = "Rewards payout in",
                            iconUrl = "https://raw.githubusercontent.com/soramitsu/shared-features-utils/master/icons/tokens/coloured/PSWAP.svg",
                            value = "PSWAP"
                        )
                    )
                    if (state.pooledBaseAmount.isNotEmpty()) {
                        InfoTableItem(
                            TitleValueViewState(
                                title = stringResource(id = R.string.pl_your_pooled_format, state.assetFrom?.symbol?.uppercase().orEmpty()),
                                value = state.pooledBaseAmount,
                                additionalValue = state.pooledBaseFiat
                            )
                        )
                    }
                    if (state.pooledTargetAmount.isNotEmpty()) {
                        InfoTableItem(
                            TitleValueViewState(
                                title = stringResource(id = R.string.pl_your_pooled_format, state.assetTo?.symbol?.uppercase().orEmpty()),
                                value = state.pooledTargetAmount,
                                additionalValue = state.pooledTargetFiat
                            )
                        )

                    }
                }
            }

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

            if (state.pooledBaseAmount.isNotEmpty()) {
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
}

@Preview
@Composable
private fun PreviewPoolDetailsScreen() {
    PoolDetailsScreen(
        state = PoolDetailsState(
            apy = "23.3%",
            tvl = "$34.999 TVL",
        ),
        callbacks = object : PoolDetailsCallbacks {
            override fun onSupplyLiquidityClick() {}
            override fun onRemoveLiquidityClick() {}
            override fun onDetailItemClick(itemId: Int) {}
        },
    )
}
