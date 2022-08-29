package jp.co.soramitsu.staking.impl.presentation.setup.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.BackgroundCornered
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.black
import jp.co.soramitsu.common.compose.theme.purple
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.StakingPoolViewModel

data class SetupStakingPoolViewState(
    val toolbarViewState: ToolbarViewState,
    val assetName: String,
    val rewardsPayoutDelay: String,
    val yearlyEstimatedEarnings: String,
    val unstakingPeriod: String
)

@Composable
fun SetupStakingPoolScreen(viewModel: StakingPoolViewModel) {
    SetupStakingPoolScreen(
        SetupStakingPoolViewState(
            ToolbarViewState("Pool staking", R.drawable.ic_arrow_back_24dp),
            "KSM",
            "2 days",
            "18%",
            "7 days"
        ),
        onNavigationClick = {}
    )
}

@Composable
private fun SetupStakingPoolScreen(state: SetupStakingPoolViewState, onNavigationClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        MarginVertical(margin = 12.dp)
        Toolbar(state = state.toolbarViewState, onNavigationClick = onNavigationClick)
        MarginVertical(margin = 8.dp)
        WhatIsStakingCard()
    }
}

@Composable
private fun WhatIsStakingCard() {
    BackgroundCornered(modifier = Modifier.fillMaxWidth()) {
        Row {
            Image(res = R.drawable.ic_book, tint = purple)

        }
    }
}

@Composable
@Preview
private fun SetupStakingPoolScreenPreview() {
    val state = SetupStakingPoolViewState(
        ToolbarViewState("Pool staking", R.drawable.ic_arrow_back_24dp),
        "KSM",
        "2 days",
        "18%",
        "7 days"
    )
    SetupStakingPoolScreen(state, {})
}
