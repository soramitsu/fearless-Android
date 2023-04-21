package jp.co.soramitsu.staking.impl.presentation.setup.pool.join

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.AccountInfo
import jp.co.soramitsu.common.compose.component.AccountInfoViewState
import jp.co.soramitsu.common.compose.component.AmountInput
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.FeeInfo
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.feature_staking_impl.R
import java.math.BigDecimal

data class SetupStakingScreenViewState(
    val toolbarViewState: ToolbarViewState,
    val accountInfoState: AccountInfoViewState,
    val amountInputViewState: AmountInputViewState,
    val feeInfoViewState: FeeInfoViewState,
    val buttonState: ButtonViewState
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SetupStakingScreen(
    state: SetupStakingScreenViewState,
    onNavigationClick: () -> Unit,
    onAmountInput: (BigDecimal?) -> Unit,
    onNextClick: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val onJoinPoolHandler = remember {
        {
            keyboardController?.hide()
            onNextClick()
        }
    }

    BottomSheetScreen(Modifier.verticalScroll(rememberScrollState())) {
        Toolbar(state = state.toolbarViewState, onNavigationClick = onNavigationClick)
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .imePadding()
        ) {
            MarginVertical(margin = 8.dp)
            AccountInfo(state = state.accountInfoState)
            MarginVertical(margin = 12.dp)
            AmountInput(state = state.amountInputViewState, onInput = onAmountInput)
            MarginVertical(margin = 16.dp)
            Spacer(modifier = Modifier.weight(1f))
            FeeInfo(state = state.feeInfoViewState)
            MarginVertical(margin = 16.dp)
            AccentButton(
                state = state.buttonState,
                onClick = onJoinPoolHandler,
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
private fun SetupStakingScreenPreview() {
    val state = SetupStakingScreenViewState(
        ToolbarViewState(title = "Join pool", navigationIcon = R.drawable.ic_arrow_back_24dp),
        AccountInfoViewState(
            address = "0xfh73fh83f28hf82h28f",
            accountName = "My account",
            image = painterResource(id = R.drawable.ic_wallet),
            caption = "Join pool from"
        ),
        AmountInputViewState(
            tokenName = "KSM",
            tokenImage = "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Karura.svg",
            totalBalance = "Balance: 20.0",
            fiatAmount = "$120.0",
            tokenAmount = BigDecimal.ONE,
            initial = null
        ),
        FeeInfoViewState(
            feeAmount = "0.0051 KSM",
            feeAmountFiat = "$0.0009"
        ),
        ButtonViewState("Join", true)
    )
    FearlessTheme {
        SetupStakingScreen(state, {}, {}, {})
    }
}
