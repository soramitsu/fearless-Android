package jp.co.soramitsu.staking.impl.presentation.staking.main.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import jp.co.soramitsu.common.utils.clickableWithNoIndication

@Composable
private fun StakingAssetInfo(
    title: String,
    GuideInfo: @Composable () -> Unit,
    MainInfo: @Composable () -> Unit
) {
    val collapseState = remember { mutableStateOf(false) }

    val chevronIcon = if (collapseState.value) {
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
                    .clickableWithNoIndication { collapseState.value = collapseState.value.not() }
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.customTypography.body1,
                    modifier = Modifier.weight(1f)
                )
                Image(res = chevronIcon, modifier = Modifier.align(Alignment.CenterVertically))
            }
            if (collapseState.value.not()) {
                GuideInfo()
                MarginVertical(margin = 16.dp)
                MainInfo()
                MarginVertical(margin = 16.dp)
            }
        }
    }
}

@Composable
fun StakingAssetInfo(state: StakingAssetInfoViewState) {
    when (state) {
        is StakingAssetInfoViewState.Parachain -> {
            ParachainStakingInfo(state)
        }
        is StakingAssetInfoViewState.RelayChain -> {
            RelaychainStakingInfo(state)
        }
        is StakingAssetInfoViewState.StakingPool -> PoolsStakingInfo(state)
    }
}

@Composable
fun ParachainStakingInfo(state: StakingAssetInfoViewState.Parachain) {
}

@Composable
fun RelaychainStakingInfo(state: StakingAssetInfoViewState.RelayChain) {
}

@Composable
fun PoolsStakingInfo(state: StakingAssetInfoViewState.StakingPool) {
    StakingAssetInfo(
        state.title,
        GuideInfo = {
            B2(text = state.guide, color = white64)
        },
        MainInfo = {
            Row {
                Column(modifier = Modifier.weight(1f)) {
                    TitleToValue(state = state.minToJoin, testTag = "minToJoin")
                    MarginVertical(margin = 16.dp)
                    TitleToValue(state = state.existingPools, testTag = "existingPools")
                    MarginVertical(margin = 16.dp)
                    TitleToValue(state = state.maxMembersInPool, testTag = "maxMembersInPool")
                }
                Column(modifier = Modifier.weight(1f)) {
                    TitleToValue(state = state.minToCreate, testTag = "minToCreate")
                    MarginVertical(margin = 16.dp)
                    TitleToValue(state = state.possiblePools, testTag = "possiblePools")
                    MarginVertical(margin = 16.dp)
                    TitleToValue(state = state.maxPoolsMembers, testTag = "maxPoolsMembers")
                }
            }
        }
    )
}

@Composable
@Preview
private fun StakingAssetInfoPreview() {
    val poolState = StakingAssetInfoViewState.StakingPool(
        title = "Kusama pool",
        guide = "Stakers (members) with a small amount of tokens can pool their funds " +
            "together and act as a single nominator. The earnings of the pool are split pro rata " +
            "to a member's stake in the bonded pool.",
        minToJoin = TitleValueViewState("Min. to join Pool", "0.0016 KSM"),
        minToCreate = TitleValueViewState("Min. to create a pool", "0.0016 KSM"),
        existingPools = TitleValueViewState("Existing pools", "59"),
        possiblePools = TitleValueViewState("Possible pools", "64"),
        maxMembersInPool = TitleValueViewState("Max members in pool", "65536"),
        maxPoolsMembers = TitleValueViewState("Max pools members", "16")
    )
    FearlessTheme {
        Column {
            PoolsStakingInfo(poolState)
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
            )
        }
    }
}
