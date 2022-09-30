package jp.co.soramitsu.staking.impl.presentation.staking.balance.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.BottomSheetLayout
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.H2
import jp.co.soramitsu.common.compose.component.InfoTable
import jp.co.soramitsu.common.compose.component.ListDialog
import jp.co.soramitsu.common.compose.component.ListDialogState
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.MenuIconItem
import jp.co.soramitsu.common.compose.component.Notification
import jp.co.soramitsu.common.compose.component.NotificationState
import jp.co.soramitsu.common.compose.component.Shimmer
import jp.co.soramitsu.common.compose.component.TextButton
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.colorAccent
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.coroutines.launch

data class ManagePoolStakeViewState(
    val total: String?,
    val claimNotification: NotificationState?,
    val redeemNotification: NotificationState?,
    val available: TitleValueViewState,
    val unstaking: TitleValueViewState,
    val poolInfo: TitleValueViewState,
    val timeBeforeRedeem: TitleValueViewState
)

private const val POOL_INFO_CLICK_IDENTIFIER = 0

enum class PoolStakeManagementOptions : ListDialogState.Item {
    Nominations {
        override val titleRes = R.string.pool_nominations
    },
    PoolInfo {
        override val titleRes = R.string.pool_staking_pool_info
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ManagePoolStakeScreen(
    state: ManagePoolStakeViewState,
    onBackClick: () -> Unit,
    onClaimClick: () -> Unit,
    onRedeemClick: () -> Unit,
    onPoolInfoClick: () -> Unit,
    onStakeMoreClick: () -> Unit,
    onUnstakeClick: () -> Unit,
    onNominationsClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    BottomSheetLayout(
        sheetContent = { sheetState ->
            ListDialog(state = ListDialogState(R.string.common_options, PoolStakeManagementOptions.values().toList()), onSelected = {
                scope.launch { sheetState.hide() }
                when (it) {
                    PoolStakeManagementOptions.Nominations -> onNominationsClick()
                    PoolStakeManagementOptions.PoolInfo -> onPoolInfoClick()
                }
            })
        },
        content = { sheetState ->
            BottomSheetScreen(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Column {
                    Toolbar(
                        state = ToolbarViewState(
                            stringResource(id = R.string.pool_stake_info),
                            R.drawable.ic_arrow_back_24dp,
                            listOf(MenuIconItem(R.drawable.ic_dots_horizontal_24, onClick = { scope.launch { sheetState.show() } }))
                        ),
                        onNavigationClick = onBackClick
                    )
                    Column(
                        Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                    ) {
                        MarginVertical(margin = 8.dp)
                        H2(text = stringResource(id = R.string.pool_your_total_stake), color = black2, modifier = Modifier.align(Alignment.CenterHorizontally))
                        MarginVertical(margin = 8.dp)
                        state.total?.let {
                            H2(text = state.total, modifier = Modifier.align(Alignment.CenterHorizontally))
                        } ?: Shimmer(
                            Modifier
                                .height(24.dp)
                                .width(170.dp)
                                .align(Alignment.CenterHorizontally)
                        )
                        state.claimNotification?.let {
                            MarginVertical(margin = 16.dp)
                            Notification(state = it, onAction = onClaimClick)
                        }
                        state.redeemNotification?.let {
                            MarginVertical(margin = 16.dp)
                            Notification(state = it, onAction = onRedeemClick)
                        }
                        MarginVertical(margin = 16.dp)
                        InfoTable(
                            items = listOf(
                                state.available,
                                state.unstaking,
                                state.poolInfo.copy(clickState = TitleValueViewState.ClickState(R.drawable.ic_info_14, POOL_INFO_CLICK_IDENTIFIER)),
                                state.timeBeforeRedeem
                            ),
                            onItemClick = { identifier ->
                                if (identifier == POOL_INFO_CLICK_IDENTIFIER) {
                                    onPoolInfoClick()
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        TextButton(text = stringResource(id = R.string.staking_bond_more_v1_9_0), onClick = onStakeMoreClick, modifier = Modifier.weight(1f))
                        MarginHorizontal(margin = 16.dp)
                        TextButton(text = stringResource(id = R.string.staking_unbond_v1_9_0), onClick = onUnstakeClick, modifier = Modifier.weight(1f))
                    }
                    MarginVertical(margin = 16.dp)
                }
            }
        }
    )
}

@Preview
@Composable
private fun ManagePoolStakeScreenPreview() {
    val state = ManagePoolStakeViewState(
        total = "10.00003 KSM",
        NotificationState(R.drawable.ic_status_warning_16, R.string.staking_alert_redeem_title, "0.49191 KSM", R.string.staking_redeem, colorAccent),
        NotificationState(R.drawable.ic_status_warning_16, R.string.staking_alert_redeem_title, "0.49191 KSM", R.string.staking_redeem, colorAccent),
        TitleValueViewState("Available"),
        TitleValueViewState("Unstaking", "1.1000 KSM", "\$1.001"),
        TitleValueViewState("Pool Info", "⚡️Everlight☀️", clickState = TitleValueViewState.ClickState(R.drawable.ic_info_14, 1)),
        TitleValueViewState("Time before redeem", "5 days")
    )
    FearlessTheme {
        ManagePoolStakeScreen(state.copy(total = null), {}, {}, {}, {}, {}, {}, {})
    }
}
