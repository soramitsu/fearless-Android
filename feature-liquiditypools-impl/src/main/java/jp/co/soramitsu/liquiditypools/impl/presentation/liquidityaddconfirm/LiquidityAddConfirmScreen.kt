package jp.co.soramitsu.liquiditypools.impl.presentation.liquidityaddconfirm

import androidx.compose.foundation.background
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
import coil.compose.SubcomposeAsyncImage
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.B1
import jp.co.soramitsu.common.compose.component.BackgroundCorneredWithBorder
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.InfoTableItem
import jp.co.soramitsu.common.compose.component.InfoTableItemAsset
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.Shimmer
import jp.co.soramitsu.common.compose.component.TitleIconValueState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.getImageRequest
import jp.co.soramitsu.common.compose.theme.backgroundBlack
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.white50
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.liquiditypools.impl.presentation.PoolsFlowViewModel.Companion.ITEM_APY_ID
import jp.co.soramitsu.liquiditypools.impl.presentation.PoolsFlowViewModel.Companion.ITEM_FEE_ID

data class LiquidityAddConfirmState(
    val assetBase: Asset? = null,
    val assetTarget: Asset? = null,
    val baseAmount: String = "",
    val targetAmount: String = "",
    val slippage: String = "0.5%",
    val apy: String? = null,
    val feeInfo: FeeInfoViewState = FeeInfoViewState.default,
    val buttonEnabled: Boolean = false,
    val buttonLoading: Boolean = false
)

interface LiquidityAddConfirmCallbacks {

    fun onConfirmClick()
    fun onAddItemClick(itemId: Int)
}

@Composable
fun LiquidityAddConfirmScreen(state: LiquidityAddConfirmState, callbacks: LiquidityAddConfirmCallbacks) {
    Column(
        modifier = Modifier
            .background(backgroundBlack)
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
                SubcomposeAsyncImage(
                    modifier = Modifier
                        .size(64.dp)
                        .offset(x = 14.dp)
                        .zIndex(1f),
                    model = getImageRequest(LocalContext.current, state.assetBase?.iconUrl.orEmpty()),
                    contentDescription = null,
                    loading = { Shimmer(Modifier.size(64.dp)) }
                )
                SubcomposeAsyncImage(
                    modifier = Modifier
                        .size(64.dp)
                        .offset(x = (-14).dp)
                        .zIndex(0f),
                    model = getImageRequest(LocalContext.current, state.assetTarget?.iconUrl.orEmpty()),
                    contentDescription = null,
                    loading = { Shimmer(Modifier.size(64.dp)) }
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
                    text = state.baseAmount,
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
                    text = state.targetAmount,
                    style = MaterialTheme.customTypography.header3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start
                )
            }

            MarginVertical(margin = 8.dp)
            B1(
                modifier = Modifier
                    .padding(horizontal = 7.dp)
                    .align(Alignment.CenterHorizontally),
                text = stringResource(id = R.string.lp_confirm_liquidity_warning_text),
                textAlign = TextAlign.Center,
                color = white50
            )
            MarginVertical(margin = 8.dp)

            BackgroundCorneredWithBorder(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Column {
                    MarginVertical(margin = 6.dp)
                    InfoTableItem(TitleValueViewState(stringResource(id = R.string.lp_slippage_title), state.slippage))
                    InfoTableItem(
                        TitleValueViewState(
                            title = stringResource(id = R.string.lp_apy_title),
                            value = state.apy,
                            clickState = TitleValueViewState.ClickState.Title(R.drawable.ic_info_14, ITEM_APY_ID)
                        ),
                        onClick = { callbacks.onAddItemClick(ITEM_APY_ID) }
                    )
                    InfoTableItemAsset(
                        TitleIconValueState(
                            title = stringResource(id = R.string.lp_reward_token_title),
                            iconUrl = "https://raw.githubusercontent.com/soramitsu/shared-features-utils/master/icons/tokens/coloured/PSWAP.svg",
                            value = "PSWAP"
                        )
                    )
                    InfoTableItem(
                        TitleValueViewState(
                            title = stringResource(id = R.string.common_network_fee),
                            value = state.feeInfo.feeAmount,
                            additionalValue = state.feeInfo.feeAmountFiat,
                            clickState = TitleValueViewState.ClickState.Title(R.drawable.ic_info_14, ITEM_FEE_ID)
                        ),
                        onClick = { callbacks.onAddItemClick(ITEM_FEE_ID) }
                    )
                    MarginVertical(margin = 8.dp)
                }
            }

            MarginVertical(margin = 24.dp)
        }

        AccentButton(
            modifier = Modifier
                .height(48.dp)
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            text = stringResource(id = R.string.common_confirm),
            enabled = state.buttonEnabled,
            loading = state.buttonLoading,
            onClick = callbacks::onConfirmClick
        )

        MarginVertical(margin = 8.dp)
    }
}

@Preview
@Composable
private fun PreviewLiquidityAddConfirmScreen() {
    LiquidityAddConfirmScreen(
        state = LiquidityAddConfirmState(
            baseAmount = "100.003 XOR",
            targetAmount = "12340443,24312 PSWAP",
            slippage = "0.5%",
            apy = "23.3%",
            feeInfo = FeeInfoViewState.default,
        ),
        callbacks = object : LiquidityAddConfirmCallbacks {
            override fun onConfirmClick() {}
            override fun onAddItemClick(itemId: Int) {}
        },
    )
}
