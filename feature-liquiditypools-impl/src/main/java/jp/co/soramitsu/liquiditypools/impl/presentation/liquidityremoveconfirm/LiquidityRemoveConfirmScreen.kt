package jp.co.soramitsu.liquiditypools.impl.presentation.liquidityremoveconfirm

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.BackgroundCorneredWithBorder
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.InfoTableItem
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.getImageRequest
import jp.co.soramitsu.common.compose.theme.backgroundBlack
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.liquiditypools.impl.presentation.PoolsFlowViewModel
import jp.co.soramitsu.liquiditypools.impl.presentation.PoolsFlowViewModel.Companion.ITEM_FEE_ID

data class LiquidityRemoveConfirmState(
    val assetFromIconUrl: String? = null,
    val assetToIconUrl: String? = null,
    val baseAmount: String = "",
    val baseFiat: String = "",
    val targetAmount: String = "",
    val targetFiat: String = "",
    val feeInfo: FeeInfoViewState = FeeInfoViewState.default,
    val buttonEnabled: Boolean = false,
    val buttonLoading: Boolean = false
)

interface LiquidityRemoveConfirmCallbacks {

    fun onRemoveConfirmClick()
    fun onRemoveConfirmItemClick(itemId: Int)
}

@Composable
fun LiquidityRemoveConfirmScreen(
    state: LiquidityRemoveConfirmState,
    callbacks: LiquidityRemoveConfirmCallbacks
) {
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
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            BackgroundCorneredWithBorder(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Column {
                    MarginVertical(margin = 16.dp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        AsyncImage(
                            model = getImageRequest(LocalContext.current, state.assetFromIconUrl.orEmpty()),
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp)
                                .offset(x = 14.dp)
                                .zIndex(1f)
                        )
                        AsyncImage(
                            model = getImageRequest(LocalContext.current, state.assetToIconUrl.orEmpty()),
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

                    InfoTableItem(
                        TitleValueViewState(
                            title = "Network fee",
                            value = state.feeInfo.feeAmount,
                            additionalValue = state.feeInfo.feeAmountFiat,
                            clickState = TitleValueViewState.ClickState.Title(R.drawable.ic_info_14, ITEM_FEE_ID)
                        ),
                        onClick = { callbacks.onRemoveConfirmItemClick(ITEM_FEE_ID) }
                    )
                }
            }

            MarginVertical(margin = 24.dp)
        }

        AccentButton(
            modifier = Modifier
                .height(48.dp)
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            text = "Confirm",
            enabled = state.buttonEnabled,
            loading = state.buttonLoading,
            onClick = callbacks::onRemoveConfirmClick
        )

        MarginVertical(margin = 8.dp)
    }
}

@Preview
@Composable
private fun PreviewLiquidityRemoveConfirmScreen() {
    LiquidityRemoveConfirmScreen(
        state = LiquidityRemoveConfirmState(
            feeInfo = FeeInfoViewState.default,
        ),
        callbacks = object : LiquidityRemoveConfirmCallbacks {
            override fun onRemoveConfirmClick() {}
            override fun onRemoveConfirmItemClick(itemId: Int) {}
        },
    )
}
