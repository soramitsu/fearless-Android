package jp.co.soramitsu.staking.impl.presentation.setup.pool.create

import androidx.compose.foundation.layout.Column
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

interface CreatePoolSetupScreenInterface {
    fun onNavigationClick()
    fun onPoolNameInput(text: String)
    fun onTokenAmountInput(text: String)
    fun onNominatorClick()
    fun onStateTogglerClick()
    fun onCreateClick()
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CreatePoolSetupScreen(
    state: CreatePoolSetupViewState,
    screenInterface: CreatePoolSetupScreenInterface
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val onCreateClickHandler = remember {
        {
            keyboardController?.hide()
            screenInterface.onCreateClick()
        }
    }
    BottomSheetScreen {
        Column(
            modifier = Modifier
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            Toolbar(
                state = ToolbarViewState(
                    title = stringResource(id = R.string.pool_create_title),
                    navigationIcon = R.drawable.ic_arrow_back_24dp
                ),
                onNavigationClick = screenInterface::onNavigationClick
            )

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                MarginVertical(margin = 16.dp)
                TextInput(state = state.poolNameInputViewState, onInput = screenInterface::onPoolNameInput)
                MarginVertical(margin = 12.dp)
                AmountInput(state = state.amountInputViewState, onInput = screenInterface::onTokenAmountInput)
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
                            onClick = screenInterface::onNominatorClick
                        )
                        MarginVertical(margin = 12.dp)
                        DropDown(
                            state = DropDownViewState(
                                state.stateToggler,
                                stringResource(id = R.string.pool_staking_state_toggler)
                            ),
                            onClick = screenInterface::onStateTogglerClick
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
                    onClick = onCreateClickHandler
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
    val emptyInterface = object : CreatePoolSetupScreenInterface {
        override fun onNavigationClick() = Unit
        override fun onPoolNameInput(text: String) = Unit
        override fun onTokenAmountInput(text: String) = Unit
        override fun onNominatorClick() = Unit
        override fun onStateTogglerClick() = Unit
        override fun onCreateClick() = Unit
    }

    FearlessTheme {
        CreatePoolSetupScreen(state = viewState, emptyInterface)
    }
}
