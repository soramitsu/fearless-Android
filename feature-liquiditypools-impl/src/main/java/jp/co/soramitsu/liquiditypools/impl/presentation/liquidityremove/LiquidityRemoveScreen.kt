package jp.co.soramitsu.liquiditypools.impl.presentation.liquidityremove

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.AmountInput
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.BackgroundCorneredWithBorder
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.InfoTableItem
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.Notification
import jp.co.soramitsu.common.compose.component.NotificationState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.theme.backgroundBlack
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.grayButtonBackground
import jp.co.soramitsu.common.compose.theme.warningOrange
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white08
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.liquiditypools.impl.presentation.PoolsFlowViewModel.Companion.ITEM_FEE_ID
import java.math.BigDecimal

data class LiquidityRemoveState(
    val baseAmountInputViewState: AmountInputViewState = AmountInputViewState.defaultObj,
    val targetAmountInputViewState: AmountInputViewState = AmountInputViewState.defaultObj,
    val transferableAmount: String? = null,
    val transferableFiat: String? = null,
    val feeInfo: FeeInfoViewState = FeeInfoViewState.default,
    val buttonEnabled: Boolean = false,
    val buttonLoading: Boolean = false
)

interface LiquidityRemoveCallbacks {

    fun onRemoveReviewClick()

    fun onRemoveBaseAmountChange(amount: BigDecimal)

    fun onRemoveBaseAmountFocusChange(isFocused: Boolean)

    fun onRemoveTargetAmountChange(amount: BigDecimal)

    fun onRemoveTargetAmountFocusChange(isFocused: Boolean)

    fun onRemoveItemClick(itemId: Int)
}

@Composable
fun LiquidityRemoveScreen(state: LiquidityRemoveState, callbacks: LiquidityRemoveCallbacks) {
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
                        state = state.baseAmountInputViewState,
                        borderColorFocused = colorAccentDark,
                        onInput = callbacks::onRemoveBaseAmountChange,
                        onInputFocusChange = callbacks::onRemoveBaseAmountFocusChange,
                        onKeyboardDone = { keyboardController?.hide() }
                    )

                    MarginVertical(margin = 8.dp)

                    AmountInput(
                        state = state.targetAmountInputViewState,
                        borderColorFocused = colorAccentDark,
                        onInput = callbacks::onRemoveTargetAmountChange,
                        onInputFocusChange = callbacks::onRemoveTargetAmountFocusChange,
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
                    InfoTableItem(
                        TitleValueViewState(
                            title = stringResource(id = R.string.assetdetails_balance_transferable),
                            value = state.transferableAmount,
                            additionalValue = state.transferableFiat
                        )
                    )
                    InfoTableItem(
                        TitleValueViewState(
                            title = stringResource(id = R.string.common_network_fee),
                            value = state.feeInfo.feeAmount,
                            additionalValue = state.feeInfo.feeAmountFiat,
                            clickState = TitleValueViewState.ClickState.Title(R.drawable.ic_info_14, ITEM_FEE_ID)
                        ),
                        onClick = { callbacks.onRemoveItemClick(ITEM_FEE_ID) }
                    )
                }
            }

            MarginVertical(margin = 24.dp)
        }

        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            Notification(
                state = NotificationState(
                    iconRes = R.drawable.ic_warning_filled,
                    title = stringResource(id = R.string.lp_pool_remove_warning_title).uppercase(),
                    value = stringResource(id = R.string.lp_pool_remove_warning_text),
                    color = warningOrange
                )
            )
        }
        MarginVertical(margin = 16.dp)

        AccentButton(
            modifier = Modifier
                .height(48.dp)
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            text = stringResource(id = R.string.common_preview),
            enabled = state.buttonEnabled,
            loading = state.buttonLoading,
            onClick = { runCallback(callbacks::onRemoveReviewClick) }
        )

        MarginVertical(margin = 8.dp)
    }
}

@Preview
@Composable
private fun PreviewLiquidityRemoveScreen() {
    LiquidityRemoveScreen(
        state = LiquidityRemoveState(
            baseAmountInputViewState = AmountInputViewState.defaultObj,
            targetAmountInputViewState = AmountInputViewState.defaultObj,
            feeInfo = FeeInfoViewState.default,
        ),
        callbacks = object : LiquidityRemoveCallbacks {
            override fun onRemoveReviewClick() {}
            override fun onRemoveBaseAmountChange(amount: BigDecimal) {}
            override fun onRemoveBaseAmountFocusChange(isFocused: Boolean) {}
            override fun onRemoveTargetAmountChange(amount: BigDecimal) {}
            override fun onRemoveTargetAmountFocusChange(isFocused: Boolean) {}
            override fun onRemoveItemClick(itemId: Int) {}
        },
    )
}
