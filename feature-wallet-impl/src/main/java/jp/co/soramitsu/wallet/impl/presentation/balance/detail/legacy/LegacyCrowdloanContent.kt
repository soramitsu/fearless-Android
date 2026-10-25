package jp.co.soramitsu.wallet.impl.presentation.balance.detail.legacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.B1
import jp.co.soramitsu.common.compose.component.BackgroundCornered
import jp.co.soramitsu.common.compose.component.InfoTableItem
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_wallet_impl.R

@Composable
fun LegacyCrowdloanContent(
    state: LoadingState<LegacyCrowdloanScreenState>,
    onOpenLockedBalance: () -> Unit
) {
    when (state) {
        is LoadingState.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }

        is LoadingState.Loaded -> Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            B1(text = state.data.description)

            if (state.data.hasEvidence) {
                BackgroundCornered {
                    Column {
                        state.data.evidenceRows.forEach { evidenceRow ->
                            InfoTableItem(state = evidenceRow)
                        }
                    }
                }
            }

            if (state.data.canOpenLockedBalance) {
                MarginVertical(margin = 4.dp)
                AccentButton(
                    text = stringResource(R.string.legacy_crowdloan_view_locked),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    onClick = onOpenLockedBalance
                )
            }
        }
    }
}
