package jp.co.soramitsu.staking.impl.presentation.staking.main.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.B2
import jp.co.soramitsu.common.compose.component.BackgroundCornered
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TitleToValue
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.blurColorLight
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.white64


@Composable
private fun StakingAssetInfo(
    state: StakingAssetInfoViewState.TitleState,
    GuideInfo: @Composable () -> Unit,
    MainInfo: @Composable () -> Unit,
    collapseClicked: () -> Unit
) {
    val chevronIcon = if (state.collapsed) {
        R.drawable.ic_chevron_down_white
    } else {
        R.drawable.ic_chevron_up_white
    }

    BackgroundCornered(
        backgroundColor = blurColorLight,
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .clickable(onClick = collapseClicked, role = Role.Button)
            ) {
                Text(
                    text = state.title,
                    style = MaterialTheme.customTypography.body1,
                    modifier = Modifier.weight(1f)
                )
                Image(res = chevronIcon, modifier = Modifier.align(Alignment.CenterVertically))
            }
            if (state.collapsed.not()) {
                GuideInfo()
                MarginVertical(margin = 16.dp)
                MainInfo()
                MarginVertical(margin = 16.dp)
            }
        }
    }
}

sealed class StakingAssetInfoViewState {
    abstract val titleState: TitleState

    data class StakingPool(
        override val titleState: TitleState,
        val guide: String,
        val minToJoin: TitleValueViewState,
        val minToCreate: TitleValueViewState,
        val existingPools: TitleValueViewState,
        val possiblePools: TitleValueViewState,
        val maxMembersInPool: TitleValueViewState,
        val maxPoolsMembers: TitleValueViewState
    ) : StakingAssetInfoViewState()

    data class RelayChainPool(
        override val titleState: TitleState,
        val stories: String, // todo stories view state
        val totalStaked: String,
        val totalStakedFiat: String,
        val minStake: String,
        val minStakeFiat: String,
        val activeNominators: String,
        val unstakingPeriod: String,
    ) : StakingAssetInfoViewState()

    data class ParachainPool(
        override val titleState: TitleState,
        val stories: String, // todo stories view state
        val minStake: String,
        val minStakeFiat: String,
        val unstakingPeriod: String,
    ) : StakingAssetInfoViewState()

    data class TitleState(
        val title: String,
        val collapsed: Boolean
    )
}

@Composable
fun StakingAssetInfo(state: StakingAssetInfoViewState, collapseClicked: () -> Unit) {
    when (state) {
        is StakingAssetInfoViewState.ParachainPool -> TODO()
        is StakingAssetInfoViewState.RelayChainPool -> TODO()
        is StakingAssetInfoViewState.StakingPool -> {
            PoolsStakingInfo(state, collapseClicked = collapseClicked)
        }
    }
}


@Composable
fun PoolsStakingInfo(state: StakingAssetInfoViewState.StakingPool, collapseClicked: () -> Unit) {
    StakingAssetInfo(
        state.titleState,
        GuideInfo = {
            B2(text = state.guide, color = white64)
        },
        MainInfo = {
            Row {
                Column(modifier = Modifier.weight(1f)) {
                    TitleToValue(state = state.minToJoin)
                    MarginVertical(margin = 16.dp)
                    TitleToValue(state = state.existingPools)
                    MarginVertical(margin = 16.dp)
                    TitleToValue(state = state.maxMembersInPool)
                }
                Column(modifier = Modifier.weight(1f)) {
                    TitleToValue(state = state.minToCreate)
                    MarginVertical(margin = 16.dp)
                    TitleToValue(state = state.possiblePools)
                    MarginVertical(margin = 16.dp)
                    TitleToValue(state = state.maxPoolsMembers)
                }
            }
        }, collapseClicked = collapseClicked
    )
}

@Composable
@Preview
fun StakingAssetInfoPreview() {
    val poolState = StakingAssetInfoViewState.StakingPool(
        StakingAssetInfoViewState.TitleState("Kusama pool", collapsed = false),
        guide = "Stakers (members) with a small amount of tokens can pool their funds together and act as a single nominator. The earnings of the pool are split pro rata to a member's stake in the bonded pool.",
        minToJoin = TitleValueViewState("Min. to join Pool", "0.0016 KSM"),
        minToCreate = TitleValueViewState("Min. to create a pool", "0.0016 KSM"),
        existingPools = TitleValueViewState("Existing pools", "59"),
        possiblePools = TitleValueViewState("Possible pools", "64"),
        maxMembersInPool = TitleValueViewState("Max members in pool", "65536"),
        maxPoolsMembers = TitleValueViewState("Max pools members", "16")
    )
    FearlessTheme {
        Column {
            StakingAssetInfo(StakingAssetInfoViewState.TitleState("Kusama", collapsed = true), GuideInfo = {}, MainInfo = {}) {}
            MarginVertical(margin = 32.dp)
            PoolsStakingInfo(poolState) {}
            MarginVertical(margin = 32.dp)
            PoolsStakingInfo(
                poolState.copy(
                    minToJoin = TitleValueViewState("Min. to join Pool", null),
                    minToCreate = TitleValueViewState("Min. to create a pool", null),
                    existingPools = TitleValueViewState("Existing pools", null),
                    possiblePools = TitleValueViewState("Possible pools", null),
                    maxMembersInPool = TitleValueViewState("Max members in pool", null),
                    maxPoolsMembers = TitleValueViewState("Max pools members", null)
                )
            ) {}
        }
    }
}
