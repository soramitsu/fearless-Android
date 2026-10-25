package jp.co.soramitsu.staking.impl.presentation.confirm.pool.join

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.feature_staking_impl.R

/** Standalone retained-transaction review; no fresh payment details or confirm action. */
@Composable
fun PoolJoinRecoveryScreen(state: PoolJoinRecoveryViewState, onBack: () -> Unit,
    onCheck: (Boolean) -> Unit, onReturn: () -> Unit) {
    BottomSheetScreen(Modifier.verticalScroll(rememberScrollState())) {
        Column {
            Toolbar(ToolbarViewState(stringResource(R.string.common_staking), R.drawable.ic_arrow_back_24dp), onNavigationClick = onBack)
            PoolJoinRecoveryContent(state, onCheck, onReturn)
        }
    }
}
