package jp.co.soramitsu.staking.impl.presentation.setup.compose

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.BackgroundCornered
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.ColoredTextButton
import jp.co.soramitsu.common.compose.component.H1
import jp.co.soramitsu.common.compose.component.H2
import jp.co.soramitsu.common.compose.component.H4
import jp.co.soramitsu.common.compose.component.H6
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TextButton
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.purple
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white50
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.setup.StartStakingPoolViewModel

data class SetupStakingPoolViewState(
    val toolbarViewState: ToolbarViewState,
    val assetName: String,
    val rewardsPayoutDelay: String,
    val yearlyEstimatedEarnings: String,
    val unstakingPeriod: String
)

@Composable
fun StartStakingPoolScreen(viewModel: StartStakingPoolViewModel) {
    val state = viewModel.state.collectAsState()
    StartStakingPoolScreen(
        state = state.value,
        onNavigationClick = viewModel::onBackClick,
        instructionsClick = viewModel::onInstructionsClick,
        joinPool = viewModel::onJoinPool,
        createPool = viewModel::onCreatePool
    )
}

@Composable
fun StartStakingPoolScreen(
    state: SetupStakingPoolViewState,
    onNavigationClick: () -> Unit,
    instructionsClick: () -> Unit,
    joinPool: () -> Unit,
    createPool: () -> Unit
) {
    BottomSheetScreen(Modifier.verticalScroll(rememberScrollState())) {
        Toolbar(state = state.toolbarViewState, onNavigationClick = onNavigationClick)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            MarginVertical(margin = 16.dp)
            WhatIsStakingCard(instructionsClick)
            MarginVertical(margin = 16.dp)
            H2(
                text = stringResource(id = R.string.staking_pool_start_earn_reward_title),
                textAlign = TextAlign.Center,
                color = black2
            )
            MarginVertical(margin = 8.dp)
            H1(
                text = state.assetName.uppercase(),
                modifier = Modifier.align(CenterHorizontally),
                color = colorAccentDark
            )
            MarginVertical(margin = 24.dp)
            SingleValueInfoCard(R.drawable.ic_chart, R.string.staking_pool_rewards_delay_text, state.rewardsPayoutDelay)
            MarginVertical(margin = 8.dp)
            SingleValueInfoCard(R.drawable.ic_money, R.string.staking_pool_start_apr_text, state.yearlyEstimatedEarnings)
            MarginVertical(margin = 8.dp)
            SingleValueInfoCard(R.drawable.ic_withdrawal, R.string.staking_pool_start_unstake_period_text, state.unstakingPeriod)
            MarginVertical(margin = 8.dp)
            SingleValueInfoCard(R.drawable.ic_gift, R.string.staking_pool_start_reward_freq_text, state.rewardsPayoutDelay)
            MarginVertical(margin = 16.dp)
            Spacer(modifier = Modifier.weight(1f))
            AccentButton(
                text = stringResource(id = R.string.staking_pool_start_join_button_title),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                onClick = joinPool
            )
            MarginVertical(margin = 8.dp)
            TextButton(
                text = stringResource(id = R.string.staking_pool_start_create_button_title),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                onClick = createPool
            )
            MarginVertical(margin = 32.dp)
        }
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
                res = R.drawable.ic_book,
                tint = purple,
                modifier = Modifier
                    .size(24.dp)
                    .align(CenterVertically)
            )
            MarginHorizontal(margin = 10.dp)
            H6(
                text = stringResource(id = R.string.pool_staking_start_about_title),
                modifier = Modifier
                    .align(CenterVertically)
                    .weight(1f),
                color = white50
            )
            MarginHorizontal(margin = 8.dp)
            ColoredTextButton(
                text = stringResource(id = R.string.common_watch),
                backgroundColor = purple,
                modifier = Modifier
                    .height(28.dp)
                    .align(CenterVertically),
                onClick = onClick
            )
        }
    }
}

@Composable
private fun SingleValueInfoCard(@DrawableRes icon: Int, @StringRes text: Int, value: String) {
    val formatted = stringResource(id = text, value)
    val startIndex = formatted.indexOf(value)
    val endIndex = startIndex + value.length
    BackgroundCornered(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Image(
                res = icon,
                tint = colorAccentDark,
                modifier = Modifier
                    .size(24.dp)
                    .align(CenterVertically)
            )
            MarginHorizontal(margin = 10.dp)
            H4(
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(color = white)) {
                        append(formatted)
                    }
                    addStyle(SpanStyle(color = colorAccentDark), startIndex, endIndex)
                },
                modifier = Modifier
                    .align(CenterVertically)
                    .weight(1f)
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
        StartStakingPoolScreen(state, {}, {}, {}, {})
    }
}
