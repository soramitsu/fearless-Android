package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import java.math.BigDecimal

data class EnterAmountViewState(
    val toolbarState: ToolbarViewState,
    val amountInputState: AmountInputViewState,
    val feeInfoState: FeeInfoViewState,
    val buttonState: ButtonViewState
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EnterAmountScreen(
    state: EnterAmountViewState,
    onNavigationClick: () -> Unit,
    onAmountInput: (BigDecimal?) -> Unit,
    onNextClick: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Toolbar(state = state.toolbarState, onNavigationClick = onNavigationClick)
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .imePadding()
        ) {
            MarginVertical(margin = 8.dp)
            AmountInput(state = state.amountInputState, onInput = onAmountInput)
            MarginVertical(margin = 16.dp)
//            Spacer(modifier = Modifier.weight(1f))
            MarginVertical(margin = 48.dp)
            FeeInfo(state = state.feeInfoState)
            MarginVertical(margin = 16.dp)
            AccentButton(
                state = state.buttonState,
                onClick = {
                    keyboardController?.hide()
                    onNextClick()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            )
            MarginVertical(margin = 16.dp)
        }
    }
}

@Composable
@Preview
private fun EnterAmountScreenPreview() {
    val state = EnterAmountViewState(
        ToolbarViewState("Stake more", R.drawable.ic_arrow_back_24dp),
        AmountInputViewState(
            "KSM",
            "",
            "1003 KSM",
            "$170000",
            BigDecimal("0.980"),
            "Amount",
            initial = null
        ),
        FeeInfoViewState(
            "Network Fee",
            "0,000001 KSM",
            "$0,00045"
        ),
        ButtonViewState("Continue", true)
    )
    FearlessTheme {
        BottomSheetScreen {
            EnterAmountScreen(state, {}, {}, {})
        }
    }
}
