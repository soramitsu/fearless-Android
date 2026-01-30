package jp.co.soramitsu.staking.impl.presentation.staking.main.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.staking.main.SolanaStakeAction
import jp.co.soramitsu.staking.impl.presentation.staking.main.SolanaStakingViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.SolanaValidatorViewState

@Composable
fun SolanaStakingOverview(
    viewState: SolanaStakingViewState,
    onActionClick: (SolanaStakeAction) -> Unit,
    onValidatorSelected: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = stringResource(id = R.string.staking_solana_placeholder_title), style = MaterialTheme.customTypography.header3)
            MarginVertical(margin = 8.dp)
            Text(text = stringResource(id = R.string.staking_solana_network_info_apr, viewState.apy), style = MaterialTheme.customTypography.body1)
            Text(text = stringResource(id = R.string.staking_solana_network_info_total, viewState.totalStake), style = MaterialTheme.customTypography.body1)

            MarginVertical(margin = 16.dp)
            Text(text = viewState.validatorsTitle, style = MaterialTheme.customTypography.header4)
            MarginVertical(margin = 8.dp)
            Text(text = stringResource(id = R.string.staking_solana_validators_hint), style = MaterialTheme.customTypography.caption)

            LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                items(viewState.validators, key = { it.voteAccount }) { item ->
                    SolanaValidatorRow(item = item, onClick = { onValidatorSelected(item.voteAccount) })
                }
            }

            MarginVertical(margin = 16.dp)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(id = R.string.staking_solana_action_delegate_cta),
                    onClick = { onActionClick(SolanaStakeAction.Delegate) }
                )
                GrayButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(id = R.string.staking_solana_action_deactivate_cta),
                    onClick = { onActionClick(SolanaStakeAction.Deactivate) }
                )
                GrayButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(id = R.string.staking_solana_action_withdraw_cta),
                    onClick = { onActionClick(SolanaStakeAction.Withdraw) }
                )
            }
        }
    }
}

@Composable
private fun SolanaValidatorRow(item: SolanaValidatorViewState, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Row {
            Text(
                text = item.rankLabel,
                style = MaterialTheme.customTypography.body2,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(text = item.identity, style = MaterialTheme.customTypography.body2)
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(text = item.stake, style = MaterialTheme.customTypography.captionBold)
            Text(text = item.commission, style = MaterialTheme.customTypography.caption)
        }
    }
}
