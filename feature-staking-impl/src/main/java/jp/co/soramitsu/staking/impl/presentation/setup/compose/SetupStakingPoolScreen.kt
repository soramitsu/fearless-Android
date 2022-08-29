package jp.co.soramitsu.staking.impl.presentation.setup.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.BackgroundCornered
import jp.co.soramitsu.common.compose.component.ColoredTextButton
import jp.co.soramitsu.common.compose.component.H6
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.purple
import jp.co.soramitsu.common.compose.theme.transparent
import jp.co.soramitsu.common.compose.theme.white50
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
        onNavigationClick = {},
        instructionsClick = {}
    )
}

@Composable
private fun SetupStakingPoolScreen(state: SetupStakingPoolViewState, onNavigationClick: () -> Unit, instructionsClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        MarginVertical(margin = 12.dp)
        Toolbar(state = state.toolbarViewState, onNavigationClick = onNavigationClick)
        MarginVertical(margin = 8.dp)
        WhatIsStakingCard(instructionsClick)
    }
}

@Composable
private fun WhatIsStakingCard(onClick: () -> Unit) {
    BackgroundCornered(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Image(
                res = R.drawable.ic_book, tint = purple, modifier = Modifier
                    .size(24.dp)
                    .align(CenterVertically)
            )
            MarginHorizontal(margin = 10.dp)
            H6(
                text = stringResource(id = R.string.pool_staking_start_about_title),
                modifier = Modifier.align(CenterVertically),
                color = white50
            )
            MarginHorizontal(margin = 8.dp)
            ColoredTextButton(
                text = stringResource(id = R.string.common_watch),
                backgroundColor = purple,
                modifier = Modifier
                    .height(24.dp)
                    .weight(1f)
                    .align(CenterVertically),
                onClick = onClick
            )
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
    FearlessTheme {
        SetupStakingPoolScreen(state, {}, {})
    }
}
