package jp.co.soramitsu.staking.impl.presentation.pools.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.MenuIconItem
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.backgroundBlack
import jp.co.soramitsu.feature_staking_impl.R

data class SelectPoolScreenViewState(
    val toolbarViewState: ToolbarViewState,
    val pools: List<PoolItemState>,
    val selectedPool: PoolItemState?,
)

@Composable
fun SelectPoolScreen(
    state: SelectPoolScreenViewState,
    onNavigationClick: () -> Unit,
    onPoolSelected: (PoolItemState) -> Unit,
    onInfoClick: (PoolItemState) -> Unit,
    onChooseClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .background(backgroundBlack)
            .fillMaxSize()
    ) {
        MarginVertical(margin = 12.dp)
        Toolbar(state = state.toolbarViewState, onNavigationClick = onNavigationClick)
        MarginVertical(margin = 8.dp)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.pools.map { it.copy(isSelected = it.id == state.selectedPool?.id) }) { pool ->
                PoolItem(
                    state = pool,
                    onSelected = onPoolSelected,
                    onInfoClick = { onInfoClick(pool) }
                )
            }
        }
        AccentButton(
            text = stringResource(id = R.string.pool_staking_choosepool_button_title),
            onClick = onChooseClick,
            enabled = state.selectedPool != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp)
        )
        MarginVertical(margin = 16.dp)
    }
}

@Preview
@Composable
private fun SelectPoolScreenPreview() {
    val items = listOf(
        PoolItemState(
            id = 1,
            name = "Polkadot js plus",
            membersCount = 15,
            staked = "20k KSM",
            isSelected = true,
            stakedAmount = BigDecimal.ZERO
        ),
        PoolItemState(
            id = 2,
            name = "POOL NUMBER ONE",
            membersCount = 7,
            staked = "10k KSM",
            isSelected = false,
            stakedAmount = BigDecimal.ZERO
        )
    )
    val state = SelectPoolScreenViewState(
        toolbarViewState = ToolbarViewState(
            "Choose a pool",
            R.drawable.ic_arrow_back_24dp,
            listOf(MenuIconItem(R.drawable.ic_dots_horizontal_24, {}))
        ),
        pools = items,
        selectedPool = items.first()
    )
    FearlessTheme {
        SelectPoolScreen(state, {}, {}, {}, {})
    }
}

