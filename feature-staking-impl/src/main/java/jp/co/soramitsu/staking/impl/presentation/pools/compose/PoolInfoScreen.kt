package jp.co.soramitsu.staking.impl.presentation.pools.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.DropDown
import jp.co.soramitsu.common.compose.component.DropDownViewState
import jp.co.soramitsu.common.compose.component.H4
import jp.co.soramitsu.common.compose.component.H5Bold
import jp.co.soramitsu.common.compose.component.InactiveDropDown
import jp.co.soramitsu.common.compose.component.InfoTable
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.greenText
import jp.co.soramitsu.common.compose.theme.red
import jp.co.soramitsu.common.compose.theme.warningOrange
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.pools.compose.PoolInfoScreenViewState.Companion.VALIDATORS_CLICK_STATE_IDENTIFIER

data class PoolInfoScreenViewState(
    val poolStatus: PoolStatusViewState,
    val poolId: TitleValueViewState,
    val name: TitleValueViewState,
    val state: TitleValueViewState,
    val staked: TitleValueViewState,
    val members: TitleValueViewState,
    val validators: TitleValueViewState,
    val depositor: DropDownViewState,
    val root: DropDownViewState?,
    val nominator: DropDownViewState?,
    val stateToggler: DropDownViewState?
) {
    companion object {
        const val VALIDATORS_CLICK_STATE_IDENTIFIER = 0
    }
}

enum class PoolStatusViewState(@StringRes val nameRes: Int, val color: Color) {
    Active(nameRes = R.string.staking_pool_status_active, color = greenText),
    Inactive(nameRes = R.string.staking_pool_status_inactive, color = red),
    ValidatorsAreNotSelected(nameRes = R.string.staking_pool_status_validators_are_not_selected, color = warningOrange)
}

interface PoolInfoScreenInterface {
    fun onCloseClick()
    fun onTableItemClick(identifier: Int)
    fun onNominatorClick()
    fun onStateTogglerClick()
}

@Composable
fun PoolInfoScreen(state: PoolInfoScreenViewState, screenInterface: PoolInfoScreenInterface) {
    BottomSheetScreen(modifier = Modifier.verticalScroll(rememberScrollState())) {
        PoolInfoToolbar(poolState = state.poolStatus, onNavigationClick = screenInterface::onCloseClick)
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            MarginVertical(margin = 8.dp)
            InfoTable(
                items = listOf(
                    state.poolId,
                    state.name,
                    state.state,
                    state.staked,
                    state.members,
                    state.validators.copy(clickState = TitleValueViewState.ClickState(R.drawable.ic_chevron_right, VALIDATORS_CLICK_STATE_IDENTIFIER))
                ),
                onItemClick = screenInterface::onTableItemClick
            )
            MarginVertical(margin = 14.dp)
            H4(text = stringResource(id = R.string.pool_staking_roles))
            MarginVertical(margin = 8.dp)
            InactiveDropDown(state = state.depositor)
            MarginVertical(margin = 12.dp)
            state.root?.let {
                InactiveDropDown(state = it)
            }
            MarginVertical(margin = 12.dp)
            state.nominator?.let {
                DropDown(state = it, onClick = screenInterface::onNominatorClick)
            }
            MarginVertical(margin = 12.dp)
            state.stateToggler?.let {
                DropDown(state = it, onClick = screenInterface::onStateTogglerClick)
            }
            MarginVertical(margin = 16.dp)
        }
    }
}

@Composable
private fun PoolInfoToolbar(poolState: PoolStatusViewState, modifier: Modifier = Modifier, onNavigationClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        jp.co.soramitsu.common.compose.component.IconButton(
            painter = painterResource(id = R.drawable.ic_close),
            tint = Color.Unspecified,
            onClick = onNavigationClick
        )
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.pool_staking_pool_info),
                style = MaterialTheme.customTypography.header4,
                maxLines = 1
            )
            PoolState(state = poolState)
        }
    }
}

@Composable
private fun PoolState(modifier: Modifier = Modifier, state: PoolStatusViewState) {
    Row(modifier = modifier) {
        Box(
            modifier = Modifier
                .background(state.color, shape = CircleShape)
                .size(6.dp)
                .align(Alignment.CenterVertically)
        )
        MarginHorizontal(margin = 4.dp)
        H5Bold(
            text = stringResource(id = state.nameRes),
            color = state.color,
            maxLines = 1,
            modifier = Modifier.align(Alignment.CenterVertically)
        )
    }
}

@Composable
@Preview
private fun PoolInfoScreenPreview() {
    val emptyInterface = object : PoolInfoScreenInterface {
        override fun onCloseClick() = Unit
        override fun onTableItemClick(identifier: Int) = Unit
        override fun onNominatorClick() = Unit
        override fun onStateTogglerClick() = Unit
    }
    val state = PoolInfoScreenViewState(
        poolId = TitleValueViewState("Index", "2"),
        name = TitleValueViewState("Name", null),
        state = TitleValueViewState("State", "Open"),
        staked = TitleValueViewState("Staked", "322.3", "$1543.43"),
        members = TitleValueViewState("Members", "43"),
        validators = TitleValueViewState("Validators", "2"),
        depositor = DropDownViewState(text = null, hint = "Depositor"),
        root = DropDownViewState(hint = "Root", text = "0x5df782gf3f487h9f238y3847fhty3g8d273gd8213ed"),
        nominator = DropDownViewState(hint = "Nominator", text = "Cool nominator"),
        stateToggler = DropDownViewState(hint = "State toggler", text = "Bad state toggler"),
        poolStatus = PoolStatusViewState.ValidatorsAreNotSelected
    )
    FearlessTheme {
        PoolInfoScreen(state, emptyInterface)
    }
}
