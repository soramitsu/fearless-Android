package jp.co.soramitsu.staking.impl.presentation.staking.main

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.api.data.StakingType

/** Remains available while balance, membership and remote pool information are loading. */
@Composable
fun PoolJoinRecoveryEntry(type: StakingType?, onClick: () -> Unit) {
    if (type == StakingType.POOL) {
        AccentButton(text = stringResource(R.string.pool_join_recovery_title),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().heightIn(min = 48.dp),
            onClick = onClick)
    }
}
