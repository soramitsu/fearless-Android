package jp.co.soramitsu.staking.impl.presentation.confirm.pool.join

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.B2
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.FullScreenLoading
import jp.co.soramitsu.common.compose.component.GradientIcon
import jp.co.soramitsu.common.compose.component.GradientIconState
import jp.co.soramitsu.common.compose.component.H1
import jp.co.soramitsu.common.compose.component.H2
import jp.co.soramitsu.common.compose.component.InfoTable
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black1
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.feature_staking_impl.R

data class ConfirmJoinPoolScreenViewState(
    val toolbarViewState: ToolbarViewState,
    val amount: String,
    val address: TitleValueViewState,
    val selectedPool: TitleValueViewState,
    val networkFee: TitleValueViewState,
    val assetIcon: GradientIconState,
    val additionalMessage: String? = null,
    val isLoading: Boolean,
    val recovery: PoolJoinRecoveryViewState? = null
)

data class PoolJoinRecoveryViewState(
    val message: String,
    val amount: String?,
    val poolId: String?,
    val network: String,
    val address: String,
    val transactionHash: String?,
    val canCheck: Boolean,
    val canCheckOlder: Boolean,
    val finalized: Boolean,
    val canReturn: Boolean = finalized,
    val create: jp.co.soramitsu.staking.impl.data.repository.PoolJoinJournal.CreateDetails? = null,
    @androidx.annotation.StringRes val returnLabelRes: Int = R.string.pool_join_recovery_return
)

@Composable
fun ConfirmJoinPoolScreen(state: ConfirmJoinPoolScreenViewState, onNavigationClick: () -> Unit, onConfirm: () -> Unit,
    onCheckPending: (Boolean) -> Unit = {}, onReturnToPool: () -> Unit = {}) {
    BottomSheetScreen(Modifier.verticalScroll(rememberScrollState())) {
        FullScreenLoading(isLoading = state.isLoading) {
            Column {
                Toolbar(state = state.toolbarViewState, onNavigationClick = onNavigationClick)
                if (state.recovery != null) {
                    PoolJoinRecoveryContent(state.recovery, onCheckPending, onReturnToPool)
                } else {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                ) {
                    MarginVertical(margin = 24.dp)
                    GradientIcon(icon = state.assetIcon, color = colorAccentDark, modifier = Modifier.align(CenterHorizontally))
                    H2(
                        text = stringResource(id = R.string.pool_staking_confirm_join_title),
                        modifier = Modifier.align(CenterHorizontally),
                        color = black2
                    )
                    MarginVertical(margin = 8.dp)
                    H1(text = state.amount, modifier = Modifier.align(CenterHorizontally))
                    MarginVertical(margin = 24.dp)
                    InfoTable(listOf(state.address, state.selectedPool, state.networkFee))
                    MarginVertical(margin = 24.dp)
                    state.additionalMessage?.let {
                        B2(text = it, color = black1)
                        MarginVertical(margin = 8.dp)
                    }
                    AccentButton(
                        text = stringResource(id = R.string.common_confirm),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        onClick = onConfirm
                    )
                    MarginVertical(margin = 16.dp)
                }
                }
            }
        }
    }
}

@Composable
internal fun PoolJoinRecoveryContent(state: PoolJoinRecoveryViewState, onCheck: (Boolean) -> Unit, onReturn: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        H2(text = stringResource(R.string.pool_join_recovery_title))
        MarginVertical(16.dp)
        B2(text = state.message)
        MarginVertical(16.dp)
        // Full public identifiers remain selectable and wrap; InfoTable abbreviates these values.
        SelectionContainer {
            Column {
                if (state.network.isNotEmpty()) {
                    B2(text = stringResource(R.string.common_network))
                    B2(text = state.network)
                }
                if (state.address.isNotEmpty()) {
                    MarginVertical(12.dp)
                    B2(text = stringResource(R.string.transaction_details_from))
                    B2(text = state.address)
                }
                state.amount?.let {
                    MarginVertical(12.dp)
                    B2(text = stringResource(R.string.common_amount))
                    B2(text = it)
                }
                state.poolId?.let {
                    MarginVertical(12.dp)
                    B2(text = stringResource(R.string.pool_staking_selected_pool))
                    B2(text = it)
                }
                state.create?.let { created ->
                    MarginVertical(12.dp)
                    B2(text = stringResource(R.string.pool_create_title))
                    MarginVertical(12.dp)
                    B2(text = stringResource(R.string.pool_staking_pool_name))
                    B2(text = created.poolName)
                    MarginVertical(12.dp)
                    B2(text = stringResource(R.string.pool_staking_nominator))
                    B2(text = created.nominatorAddress)
                    MarginVertical(12.dp)
                    B2(text = stringResource(R.string.pool_recovery_state_control))
                    B2(text = created.stateTogglerAddress)
                }
                state.transactionHash?.let {
                    MarginVertical(12.dp)
                    B2(text = stringResource(R.string.pool_join_recovery_hash))
                    B2(text = it)
                }
            }
        }
        MarginVertical(24.dp)
        if (state.canReturn) {
            AccentButton(text = stringResource(state.returnLabelRes),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = onReturn)
        } else {
            AccentButton(text = stringResource(R.string.pool_join_recovery_recent), enabled = state.canCheck,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = { onCheck(false) })
            if (state.canCheckOlder) {
                MarginVertical(12.dp)
                AccentButton(text = stringResource(R.string.pool_join_recovery_older),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = { onCheck(true) })
            }
        }
        MarginVertical(16.dp)
    }
}

@Preview
@Composable
private fun ConfirmJoinPoolScreenPreview() {
    val state = ConfirmJoinPoolScreenViewState(
        toolbarViewState = ToolbarViewState("Confirm", R.drawable.ic_arrow_back_24dp),
        amount = "10 KSM",
        address = TitleValueViewState("From", "Account for join", "0x3784348729384923849223423"),
        selectedPool = TitleValueViewState("Selected Pool", "Pool #1", "id: 1"),
        networkFee = TitleValueViewState("Network Fee", "0.0051 KSM", "$0.32"),
        assetIcon = GradientIconState.Remote("https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/assets/icons/chains/white/Karura.svg", "ffffff"),
        isLoading = false
    )
    FearlessTheme {
        ConfirmJoinPoolScreen(state, {}, {})
    }
}
