package jp.co.soramitsu.walletconnect.impl.presentation.connectioninfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.CollapsibleView
import jp.co.soramitsu.common.compose.component.InfoItem
import jp.co.soramitsu.common.compose.component.InfoItemSet
import jp.co.soramitsu.common.compose.component.InfoItemSetViewState
import jp.co.soramitsu.common.compose.component.InfoItemViewState
import jp.co.soramitsu.common.compose.component.InfoTableItem
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.WalletNameItem
import jp.co.soramitsu.common.compose.component.WalletNameItemViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme

data class ConnectInfoViewState(
    val session: InfoItemViewState,
    val permissions: InfoItemSetViewState?,
    val wallets: List<WalletNameItemViewState>,
    val expireDate: String
) {
    companion object {
        val default = ConnectInfoViewState(
            session = InfoItemViewState.default,
            permissions = null,
            wallets = listOf(),
            expireDate = "Tue, Aug 23, 2023"
        )
    }
}

interface ConnectionInfoScreenInterface {
    fun onClose()
    fun onDisconnectClick()
}

@Composable
fun ConnectionInfoContent(state: ConnectInfoViewState, callback: ConnectionInfoScreenInterface) {
    BottomSheetScreen {
        Column(modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection())) {
            Toolbar(state = ToolbarViewState(stringResource(id = R.string.connection_details_title), R.drawable.ic_arrow_back_24dp), onNavigationClick = callback::onClose)
            MarginVertical(margin = 16.dp)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoItem(
                    state = state.session
                )

                state.permissions?.let {
                    CollapsibleView(
                        title = stringResource(id = R.string.connection_review_permissions),
                        initCollapsed = false
                    ) {
                        InfoItemSet(state = it)
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.wallets.map { it.copy(isSelected = true) }.map { walletItemState ->
                        WalletNameItem(
                            state = walletItemState,
                            onSelected = {}
                        )
                    }
                }

                InfoTableItem(
                    state = TitleValueViewState(
                        "Expiry",
                        state.expireDate
                    )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .wrapContentHeight()
            ) {
                MarginVertical(margin = 8.dp)
                AccentButton(
                    text = stringResource(id = R.string.common_disconnect),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 16.dp),
                    onClick = callback::onDisconnectClick
                )

                MarginVertical(margin = 16.dp)
            }
        }
    }
}

@Preview
@Composable
private fun WalletConnectPreview() {
    val state = ConnectInfoViewState(
        session = InfoItemViewState(
            title = "Dapp Name",
            subtitle = "some_url_to_dapp"
        ),
        permissions = InfoItemSetViewState(
            title = "Some required chain name",
            infoItems = listOf(
                InfoItemViewState(
                    title = "Methods",
                    subtitle = "eth_sendTransaction, personal_sign"
                ),
                InfoItemViewState(
                    title = "Events",
                    subtitle = "accountsChanged, chainChanged"
                )
            )
        ),
        wallets = listOf(),
        expireDate = "Tue, Aug 23, 2023"
    )

    val emptyCallback = object : ConnectionInfoScreenInterface {
        override fun onClose() {}
        override fun onDisconnectClick() {}
    }

    FearlessTheme {
        ConnectionInfoContent(
            state = state,
            callback = emptyCallback
        )
    }
}
