package jp.co.soramitsu.staking.impl.presentation.staking.main.compose

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.BackgroundCornered
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.Timer
import jp.co.soramitsu.common.compose.component.TitleToValue
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.blurColorLight
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.white16
import jp.co.soramitsu.common.utils.clickableWithNoIndication
import jp.co.soramitsu.feature_staking_impl.R

internal sealed interface StakeStatusTrailingContent {
    data class Countdown(
        val timeLeft: Long,
        val extraMessage: String?,
        val hideZeroTimer: Boolean
    ) : StakeStatusTrailingContent

    data class Message(val value: String) : StakeStatusTrailingContent

    object None : StakeStatusTrailingContent
}

internal fun StakeStatus.trailingContent(): StakeStatusTrailingContent = when {
    this is StakeStatus.WithTimer -> StakeStatusTrailingContent.Countdown(
        timeLeft = sanitizeStakingTimerMillis(timeLeft),
        extraMessage = extraMessage,
        hideZeroTimer = hideZeroTimer
    )
    extraMessage != null -> StakeStatusTrailingContent.Message(extraMessage)
    else -> StakeStatusTrailingContent.None
}

@Composable
private fun StakingInfoItem(
    title: String,
    MainInfo: @Composable () -> Unit,
    Status: @Composable () -> Unit,
    onClick: () -> Unit
) {
    BackgroundCornered(
        backgroundColor = blurColorLight,
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickableWithNoIndication(onClick = onClick)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.customTypography.body1,
                    modifier = Modifier.weight(1f)
                )
                Image(res = R.drawable.ic_dots_horizontal_24, modifier = Modifier.align(CenterVertically))
            }
            MainInfo()
            MarginVertical(margin = 16.dp)
            Divider(
                color = white16,
                modifier = Modifier
                    .height(1.dp)
                    .fillMaxWidth()
            )
            MarginVertical(margin = 16.dp)
            Status()
            MarginVertical(margin = 16.dp)
        }
    }
}

@Composable
fun StakingPoolInfo(state: StakeInfoViewState, onClick: () -> Unit) {
    require(state is StakeInfoViewState.PoolStakeInfoViewState)
    StakingInfoItem(
        title = state.title,
        MainInfo = {
            Column {
                Row {
                    TitleToValue(state = state.staked, testTag = "poolStaked", modifier = Modifier.weight(1f))
                    TitleToValue(state = state.rewarded, testTag = "poolRewarded", modifier = Modifier.weight(1f))
                }
                MarginVertical(margin = 16.dp)
                Row {
                    TitleToValue(state = state.redeemable, testTag = "poolRedeemable", modifier = Modifier.weight(1f))
                    TitleToValue(state = state.unstaking, testTag = "poolUnstaking", modifier = Modifier.weight(1f))
                }
            }
        },
        Status = {
            StakeStatus(state.status)
        },
        onClick
    )
}

@Composable
fun StakingRelayChainInfo(
    state: StakeInfoViewState.RelayChainStakeInfoViewState,
    onClick: () -> Unit,
    onStatusClick: () -> Unit
) {
    StakingInfoItem(
        title = state.title,
        MainInfo = {
            Row {
                TitleToValue(state = state.staked, testTag = "relayStaked", modifier = Modifier.weight(1f))
                TitleToValue(state = state.rewarded, testTag = "relayRewarded", modifier = Modifier.weight(1f))
            }
        },
        Status = {
            val modifier = if (state.status.statusClickable) {
                Modifier.clickableWithNoIndication(onClick = onStatusClick)
            } else {
                Modifier
            }
            StakeStatus(state.status, modifier)
        },
        onClick = onClick
    )
}

@Composable
fun StakingParachainInfo(
    state: StakeInfoViewState.ParachainStakeInfoViewState,
    onClick: () -> Unit
) {
    StakingInfoItem(
        title = state.title,
        MainInfo = {
            Row {
                TitleToValue(state = state.staked, testTag = "parachainStaked", modifier = Modifier.weight(1f))
                TitleToValue(state = state.rewards, testTag = "parachainRewards", modifier = Modifier.weight(1f))
            }
        },
        Status = {
            StakeStatus(state.status)
        },
        onClick = onClick
    )
}

@Composable
fun StakeStatus(state: StakeStatus, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        StatusText(state.textRes, state.tintRes, modifier = Modifier.weight(1f))
        when (val trailingContent = state.trailingContent()) {
            is StakeStatusTrailingContent.Countdown -> Timer(
                millis = trailingContent.timeLeft,
                extraMessage = trailingContent.extraMessage,
                hideZeroTimer = trailingContent.hideZeroTimer,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
            is StakeStatusTrailingContent.Message -> Text(
                text = trailingContent.value,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
            StakeStatusTrailingContent.None -> Unit
        }
    }
}

@Composable
fun StatusText(
    @StringRes textRes: Int,
    @ColorRes tintRes: Int,
    modifier: Modifier
) {
    Row(modifier = modifier) {
        Box(
            modifier = Modifier
                .background(colorResource(id = tintRes), CircleShape)
                .size(8.dp)
                .align(CenterVertically)
        )
        MarginHorizontal(margin = 8.dp)
        Text(
            modifier = Modifier.align(CenterVertically),
            text = stringResource(id = textRes).uppercase(),
            style = MaterialTheme.customTypography.capsTitle2.copy(color = colorResource(id = tintRes))
        )
    }
}

@Preview
@Composable
private fun StakingInfoItemPreview() {
    val poolState = StakeInfoViewState.PoolStakeInfoViewState(
        title = "Your pool staking",
        TitleValueViewState("Staked", "10 KSM", "$4,530"),
        TitleValueViewState("Rewarded", null),
        TitleValueViewState("Redeemable", "1 KSM", "$4,53"),
        TitleValueViewState("Unstaking", "10 KSM", "$4,530"),
        StakeStatus.PoolActive(123123L, true)
    )
    FearlessTheme {
        StakingPoolInfo(poolState) {}
    }
}
