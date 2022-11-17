package jp.co.soramitsu.staking.impl.presentation.pools.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.DropDown
import jp.co.soramitsu.common.compose.component.DropDownViewState
import jp.co.soramitsu.common.compose.component.H4
import jp.co.soramitsu.common.compose.component.InactiveDropDown
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TextInput
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.feature_staking_impl.R

data class EditPoolViewState(
    val poolName: String,
    val depositor: String,
    val root: String,
    val nominator: String,
    val stateToggler: String,
    val continueAvailable: Boolean
)

interface EditPoolScreenInterface {
    fun onCloseClick()
    fun onNameInput(text: String)
    fun onClearNameClick()
    fun onRootClick()
    fun onNominatorClick()
    fun onStateTogglerClick()
    fun onNextClick()
}

@Composable
fun EditPoolScreen(state: EditPoolViewState, screenInterface: EditPoolScreenInterface) {
    BottomSheetScreen {
        Toolbar(
            state = ToolbarViewState(
                title = stringResource(id = R.string.pool_edit_title),
                navigationIcon = R.drawable.ic_close
            ),
            onNavigationClick = screenInterface::onCloseClick
        )
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .imePadding()
        ) {
            MarginVertical(margin = 8.dp)
            val nameInputState = TextInputViewState(
                text = state.poolName,
                hint = stringResource(id = R.string.pool_staking_pool_name),
                endIcon = R.drawable.ic_close_16_circle
            )
            TextInput(state = nameInputState, onInput = screenInterface::onNameInput, onEndIconClick = screenInterface::onClearNameClick)
            MarginVertical(margin = 24.dp)
            H4(text = stringResource(id = R.string.pool_staking_roles))
            MarginVertical(8.dp)
            InactiveDropDown(text = state.depositor, hint = R.string.pool_staking_depositor)
            MarginVertical(8.dp)
            val rootState = DropDownViewState(
                text = state.root,
                hint = stringResource(id = R.string.pool_staking_root)
            )
            DropDown(state = rootState, onClick = screenInterface::onRootClick)
            MarginVertical(8.dp)
            val nominatorState = DropDownViewState(
                text = state.nominator,
                hint = stringResource(id = R.string.pool_staking_nominator)
            )
            DropDown(state = nominatorState, onClick = screenInterface::onNominatorClick)
            MarginVertical(8.dp)
            val stateTogglerState = DropDownViewState(
                text = state.stateToggler,
                hint = stringResource(id = R.string.pool_staking_state_toggler)
            )
            DropDown(state = stateTogglerState, onClick = screenInterface::onStateTogglerClick)
            Spacer(modifier = Modifier.weight(1f))
            AccentButton(
                text = stringResource(id = R.string.common_continue),
                enabled = state.continueAvailable,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                onClick = screenInterface::onNextClick
            )
            MarginVertical(margin = 32.dp)
        }
    }
}

@Composable
@Preview
private fun EditPoolScreenPreview() {
    FearlessTheme {
        val state = EditPoolViewState(
            poolName = "CoolPool",
            depositor = "Cmfq9RuZpxPjVKwGGYrk4WAPfHJKKzxpndFbvronBR9C5TW",
            root = "Cmfq9RuZpxPjVKwGGYrk4WAPfHJKKzxpndFbvronBR9C5TW",
            nominator = "Cmfq9RuZpxPjVKwGGYrk4WAPfHJKKzxpndFbvronBR9C5TW",
            stateToggler = "Cmfq9RuZpxPjVKwGGYrk4WAPfHJKKzxpndFbvronBR9C5TW",
            continueAvailable = true
        )
        val screenInterface = object : EditPoolScreenInterface {
            override fun onCloseClick() = Unit
            override fun onNameInput(text: String) = Unit
            override fun onClearNameClick() = Unit
            override fun onRootClick() = Unit
            override fun onNominatorClick() = Unit
            override fun onStateTogglerClick() = Unit
            override fun onNextClick() = Unit
        }
        EditPoolScreen(state = state, screenInterface = screenInterface)
    }
}
