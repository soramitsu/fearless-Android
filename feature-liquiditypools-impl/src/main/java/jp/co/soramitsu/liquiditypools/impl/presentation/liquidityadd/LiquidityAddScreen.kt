package jp.co.soramitsu.liquiditypools.impl.presentation.liquidityadd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.AmountInput
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.BackgroundCorneredWithBorder
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.DoubleGradientIcon
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.GradientIconState
import jp.co.soramitsu.common.compose.component.H4Bold
import jp.co.soramitsu.common.compose.component.InfoTable
import jp.co.soramitsu.common.compose.component.InfoTableItem
import jp.co.soramitsu.common.compose.component.InfoTableItemAsset
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.NavigationIconButton
import jp.co.soramitsu.common.compose.component.TitleIconValueState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.theme.alertYellow
import jp.co.soramitsu.common.compose.theme.backgroundBlack
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.grayButtonBackground
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white08
import jp.co.soramitsu.feature_wallet_impl.R

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

    fun onNavigationClick()

    fun onReviewClick()

    fun onFromAmountChange(amount: BigDecimal)

    fun onFromAmountFocusChange(isFocused: Boolean)

    fun onToAmountChange(amount: BigDecimal)

    fun onToAmountFocusChange(isFocused: Boolean)
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
        Toolbar(callbacks)

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
                        onInput = callbacks::onFromAmountChange,
                        onInputFocusChange = callbacks::onFromAmountFocusChange,
                        onKeyboardDone = { keyboardController?.hide() }
                    )

                    MarginVertical(margin = 8.dp)

                    AmountInput(
                        state = state.toAmountInputViewState,
                        borderColorFocused = colorAccentDark,
                        onInput = callbacks::onToAmountChange,
                        onInputFocusChange = callbacks::onToAmountFocusChange,
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
                            clickState = TitleValueViewState.ClickState.Title(R.drawable.ic_info_14, 1)
                        )
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
                            clickState = TitleValueViewState.ClickState.Title(R.drawable.ic_info_14, 2)
                        )
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
            onClick = callbacks::onReviewClick
        )

        MarginVertical(margin = 8.dp)
    }
}

@Composable
private fun Toolbar(callback: LiquidityAddCallbacks) {
    Row(
        modifier = Modifier
            .wrapContentHeight()
            .padding(bottom = 12.dp)
    ) {
        NavigationIconButton(
            modifier = Modifier.padding(start = 16.dp),
            onNavigationClick = callback::onNavigationClick
        )

        H4Bold(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically),
            text = "Supply liquidity",
            textAlign = TextAlign.Center
        )

        NavigationIconButton(
            modifier = Modifier
                .align(Alignment.Top)
                .padding(end = 16.dp),
            navigationIconResId = R.drawable.ic_cross_32,
            onNavigationClick = callback::onNavigationClick
        )
    }
}

@Preview
@Composable
private fun PreviewLiquidityAddScreen() {
    BottomSheetScreen {
        LiquidityAddScreen(
            state = LiquidityAddState(
                fromAmountInputViewState = AmountInputViewState.defaultObj,
                toAmountInputViewState = AmountInputViewState.defaultObj,
                apy = "23.3%",
                feeInfo = FeeInfoViewState.default,
                slippage = "0.5%"
            ),
            callbacks = object : LiquidityAddCallbacks {
                override fun onNavigationClick() {}
                override fun onReviewClick() {}
                override fun onFromAmountChange(amount: BigDecimal) {}
                override fun onFromAmountFocusChange(isFocused: Boolean) {}
                override fun onToAmountChange(amount: BigDecimal) {}
                override fun onToAmountFocusChange(isFocused: Boolean) {}
            },
        )
    }
}
