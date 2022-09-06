package jp.co.soramitsu.staking.impl.presentation.pools.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.H4
import jp.co.soramitsu.common.compose.component.InfoTable
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.feature_staking_impl.R

data class PoolInfoScreenViewState(
    val poolId: TitleValueViewState,
    val name: TitleValueViewState,
    val state: TitleValueViewState,
    val staked: TitleValueViewState,
    val members: TitleValueViewState,
    val depositor: TitleValueViewState?,
    val root: TitleValueViewState?,
    val nominator: TitleValueViewState?,
    val stateToggler: TitleValueViewState?
)

@Composable
fun PoolInfoScreen(state: PoolInfoScreenViewState, onCloseClick: () -> Unit) {
    BottomSheetScreen(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Toolbar(
            state = ToolbarViewState(
                title = stringResource(id = R.string.pool_staking_pool_info),
                navigationIcon = R.drawable.ic_close
            ),
            onNavigationClick = onCloseClick
        )
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            MarginVertical(margin = 8.dp)
            InfoTable(
                items = listOf(
                    state.poolId,
                    state.name,
                    state.state,
                    state.staked,
                    state.members
                )
            )
            MarginVertical(margin = 14.dp)
            val roles = listOfNotNull(
                state.depositor,
                state.root,
                state.nominator,
                state.stateToggler
            )
            if (roles.isNotEmpty()) {
                H4(text = stringResource(id = R.string.pool_staking_roles))
                MarginVertical(margin = 8.dp)
                InfoTable(items = roles)
            }
            MarginVertical(margin = 16.dp)
        }
    }
}

@Composable
@Preview
private fun PoolInfoScreenPreview() {
    val state = PoolInfoScreenViewState(
        poolId = TitleValueViewState("Index", "2"),
        name = TitleValueViewState("Name", "Pool number 2"),
        state = TitleValueViewState("State", "Open"),
        staked = TitleValueViewState("Staked", "322.3", "$1543.43"),
        members = TitleValueViewState("Members", "43"),
        depositor = TitleValueViewState("Depositor", null),
        root = TitleValueViewState("Root", "0x5df782gf3f487h9f238y3847fhty3g8d273gd8213ed"),
        nominator = TitleValueViewState("Nominator", "Cool nominator"),
        stateToggler = TitleValueViewState("State toggler", "Bad state toggler")
    )
    FearlessTheme {
        PoolInfoScreen(state, {})
    }
}
