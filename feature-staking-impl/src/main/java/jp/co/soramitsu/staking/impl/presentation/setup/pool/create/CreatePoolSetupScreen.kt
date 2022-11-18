package jp.co.soramitsu.staking.impl.presentation.setup.pool.create

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.AdvancedBlock
import jp.co.soramitsu.common.compose.component.AmountInput
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.DropDown
import jp.co.soramitsu.common.compose.component.DropDownViewState
import jp.co.soramitsu.common.compose.component.FeeInfo
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.InactiveDropDown
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TextInput
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.feature_staking_impl.R

data class CreatePoolSetupViewState(
    val poolNameInputViewState: TextInputViewState,
    val amountInputViewState: AmountInputViewState,
    val poolId: String,
    val depositor: String,
    val root: String,
    val nominator: String,
    val stateToggler: String,
    val feeInfoViewState: FeeInfoViewState,
    val createButtonViewState: ButtonViewState
)

@Composable
fun CreatePoolSetupScreen(
    state: CreatePoolSetupViewState,
    onNavigationClick: () -> Unit,
    onPoolNameInput: (String) -> Unit,
    onTokenAmountInput: (String) -> Unit,
    onNominatorClick: () -> Unit,
    onStateTogglerClick: () -> Unit,
    onCreateClick: () -> Unit
) {
    BottomSheetScreen {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
        ) {
            Toolbar(
                state = ToolbarViewState(
                    title = stringResource(id = R.string.pool_create_title),
                    navigationIcon = R.drawable.ic_arrow_back_24dp
                ),
                onNavigationClick = onNavigationClick
            )

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                MarginVertical(margin = 16.dp)
                TextInput(state = state.poolNameInputViewState, onInput = onPoolNameInput)
                MarginVertical(margin = 12.dp)
                AmountInput(state = state.amountInputViewState, onInput = onTokenAmountInput)
                MarginVertical(margin = 12.dp)
                AdvancedBlock(
                    modifier = Modifier,
                    initialState = false,
                    Content = {
                        InactiveDropDown(state.poolId, R.string.pool_staking_pool_id)
                        MarginVertical(margin = 12.dp)
                        InactiveDropDown(state.depositor, R.string.pool_staking_depositor)
                        MarginVertical(margin = 12.dp)
                        InactiveDropDown(state.root, R.string.pool_staking_root)
                        MarginVertical(margin = 12.dp)
                        DropDown(
                            state = DropDownViewState(
                                state.nominator,
                                stringResource(id = R.string.pool_staking_nominator)
                            ),
                            onClick = onNominatorClick
                        )
                        MarginVertical(margin = 12.dp)
                        DropDown(
                            state = DropDownViewState(
                                state.stateToggler,
                                stringResource(id = R.string.pool_staking_state_toggler)
                            ),
                            onClick = onStateTogglerClick
                        )
                    }
                )
                MarginVertical(margin = 32.dp)
                FeeInfo(state = state.feeInfoViewState)
                MarginVertical(margin = 8.dp)
                AccentButton(
                    state = state.createButtonViewState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    onClick = onCreateClick
                )
                MarginVertical(margin = 16.dp)
            }
        }
    }
}

@Composable
@Preview
private fun CreatePoolSetupScreenPreview() {
    val viewState = CreatePoolSetupViewState(
        TextInputViewState("entering pool name", "Pool name"),
        AmountInputViewState(
            tokenName = "KSM",
            tokenImage = "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Karura.svg",
            totalBalance = "Balance: 20.0",
            fiatAmount = "$120.0",
            tokenAmount = "0.1"
        ),
        "7",
        "⚡️Everlight☀️",
        "⚡️Everlight☀️",
        "⚡️Everlight☀️",
        "⚡️Everlight☀️",
        FeeInfoViewState(
            feeAmount = "0.0051 KSM",
            feeAmountFiat = "$0.0009"
        ),
        ButtonViewState("Create", true)
    )

    FearlessTheme {
        CreatePoolSetupScreen(state = viewState, {}, {}, {}, {}, {}, {})
    }
}
