package jp.co.soramitsu.liquiditypools.impl.presentation.liquidityadd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.AmountInput
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.BackgroundCorneredWithBorder
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.InfoTableItem
import jp.co.soramitsu.common.compose.component.InfoTableItemAsset
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TitleIconValueState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.theme.backgroundBlack
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.grayButtonBackground
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white08
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.liquiditypools.impl.presentation.PoolsFlowViewModel
import jp.co.soramitsu.liquiditypools.impl.presentation.PoolsFlowViewModel.Companion.ITEM_APY_ID
import jp.co.soramitsu.liquiditypools.impl.presentation.PoolsFlowViewModel.Companion.ITEM_FEE_ID

data class LiquidityAddState(
    val fromAmountInputViewState: AmountInputViewState = AmountInputViewState.defaultObj,
    val toAmountInputViewState: AmountInputViewState = AmountInputViewState.defaultObj,
    val slippage: String = "0.5%",
    val apy: String? = null,
    val feeInfo: FeeInfoViewState = FeeInfoViewState.default,
    val buttonEnabled: Boolean = false,
    val buttonLoading: Boolean = false
)

interface LiquidityAddCallbacks {

    fun onAddReviewClick()

    fun onAddFromAmountChange(amount: BigDecimal)

    fun onAddFromAmountFocusChange(isFocused: Boolean)

    fun onAddToAmountChange(amount: BigDecimal)

    fun onAddToAmountFocusChange(isFocused: Boolean)

    fun onAddTableItemClick(itemId: Int)
}

@Composable
fun LiquidityAddScreen(
    state: LiquidityAddState,
    callbacks: LiquidityAddCallbacks
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val runCallback: (() -> Unit) -> Unit = { block ->
        keyboardController?.hide()
        block()
    }

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

            Box(
                modifier = Modifier
                    .padding(top = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column {
                    AmountInput(
                        state = state.fromAmountInputViewState,
                        borderColorFocused = colorAccentDark,
                        onInput = callbacks::onAddFromAmountChange,
                        onInputFocusChange = callbacks::onAddFromAmountFocusChange,
                        onKeyboardDone = { keyboardController?.hide() }
                    )

                    MarginVertical(margin = 8.dp)

                    AmountInput(
                        state = state.toAmountInputViewState,
                        borderColorFocused = colorAccentDark,
                        onInput = callbacks::onAddToAmountChange,
                        onInputFocusChange = callbacks::onAddToAmountFocusChange,
                        onKeyboardDone = { keyboardController?.hide() }
                    )
                }

                Icon(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(grayButtonBackground)
                        .border(width = 1.dp, color = white08, shape = CircleShape)
                        .padding(8.dp),
                    painter = painterResource(R.drawable.ic_plus_white_24),
                    contentDescription = null,
                    tint = white
                )
            }

            MarginVertical(margin = 24.dp)

            BackgroundCorneredWithBorder(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Column {
                    InfoTableItem(TitleValueViewState("Slippage", state.slippage))
                    InfoTableItem(
                        TitleValueViewState(
                            title = "Strategic bonus APY",
                            value = state.apy,
                            clickState = TitleValueViewState.ClickState.Title(R.drawable.ic_info_14, ITEM_APY_ID)
                        ),
                        onClick = {
                            callbacks.onAddTableItemClick(ITEM_APY_ID)
                        }
                    )
                    InfoTableItemAsset(
                        TitleIconValueState(
                            title = "Rewards payout in",
                            iconUrl = "https://raw.githubusercontent.com/soramitsu/shared-features-utils/master/icons/tokens/coloured/PSWAP.svg",
                            value = "PSWAP"
                        )
                    )
                    InfoTableItem(
                        TitleValueViewState(
                            title = "Network fee",
                            value = state.feeInfo.feeAmount,
                            additionalValue = state.feeInfo.feeAmountFiat,
                            clickState = TitleValueViewState.ClickState.Title(R.drawable.ic_info_14, ITEM_FEE_ID)
                        ),
                        onClick = {
                            callbacks.onAddTableItemClick(ITEM_FEE_ID)
                        }
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
            text = "Review",
            enabled = state.buttonEnabled,
            loading = state.buttonLoading,
            onClick = { runCallback(callbacks::onAddReviewClick) }
        )

        MarginVertical(margin = 8.dp)
    }
}

@Preview
@Composable
private fun PreviewLiquidityAddScreen() {
    LiquidityAddScreen(
        state = LiquidityAddState(
            fromAmountInputViewState = AmountInputViewState.defaultObj,
            toAmountInputViewState = AmountInputViewState.defaultObj,
            apy = "23.3%",
            feeInfo = FeeInfoViewState.default,
            slippage = "0.5%"
        ),
        callbacks = object : LiquidityAddCallbacks {
            override fun onAddReviewClick() {}
            override fun onAddFromAmountChange(amount: BigDecimal) {}
            override fun onAddFromAmountFocusChange(isFocused: Boolean) {}
            override fun onAddToAmountChange(amount: BigDecimal) {}
            override fun onAddToAmountFocusChange(isFocused: Boolean) {}
            override fun onAddTableItemClick(itemId: Int) {}
        },
    )
}
