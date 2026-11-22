package jp.co.soramitsu.irohaconnect.impl.presentation

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
import jp.co.soramitsu.common.compose.component.AttentionMessage
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.InfoItem
import jp.co.soramitsu.common.compose.component.InfoItemSet
import jp.co.soramitsu.common.compose.component.InfoItemSetViewState
import jp.co.soramitsu.common.compose.component.InfoItemViewState
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.WalletNameItem
import jp.co.soramitsu.common.compose.component.WalletNameItemViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectPhase

interface IrohaConnectScreenCallbacks {
    fun onAccountSelected(address: String)
    fun onApproveSession()
    fun onRejectSession()
    fun onApproveRequest()
    fun onRejectRequest()
    fun onDisconnect()
    fun onDismissMessage()
    fun onClose()
}

@Suppress("SpreadOperator") // stringResource takes vararg format arguments.
@Composable
private fun IrohaConnectRow.text(): String =
    value ?: valueRes?.let { stringResource(it, *valueArgs.toTypedArray()) }.orEmpty()

@Composable
private fun Section(section: IrohaConnectSection) {
    InfoItemSet(
        state = InfoItemSetViewState(
            title = section.title?.let { stringResource(it) },
            infoItems = section.rows.map { row -> InfoItemViewState(title = stringResource(row.label), subtitle = row.text()) }
        )
    )
}

@Composable
fun IrohaConnectContent(state: IrohaConnectViewState, callbacks: IrohaConnectScreenCallbacks) {
    BottomSheetScreen {
        Column(modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection())) {
            Toolbar(
                state = ToolbarViewState(title = stringResource(R.string.iroha_connect_title), navigationIcon = R.drawable.ic_arrow_back_24dp),
                onNavigationClick = callbacks::onClose
            )
            MarginVertical(margin = 16.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.appName != null) {
                    InfoItem(
                        state = InfoItemViewState(title = state.appName, subtitle = state.appUrl, placeholderIcon = R.drawable.ic_dapp_connection)
                    )
                }
                state.message?.let { AttentionMessage(message = stringResource(it)) }
                if (state.phase == IrohaConnectPhase.CONNECTING) {
                    AttentionMessage(message = stringResource(R.string.iroha_connect_connecting))
                }
                if (state.sessionRows.isNotEmpty()) Section(IrohaConnectSection(null, state.sessionRows))
                state.permissions?.let { Section(it) }

                when (state.phase) {
                    IrohaConnectPhase.SESSION_APPROVAL -> {
                        if (state.accounts.isEmpty()) {
                            AttentionMessage(message = stringResource(R.string.iroha_connect_no_accounts))
                        } else {
                            InfoItem(state = InfoItemViewState(title = stringResource(R.string.iroha_connect_choose_account), subtitle = null))
                            state.accounts.forEachIndexed { index, account ->
                                WalletNameItem(
                                    state = WalletNameItemViewState(
                                        id = index.toLong(),
                                        title = account.name,
                                        walletIcon = R.drawable.ic_wallet,
                                        isSelected = account.selected,
                                        subtitle = account.address
                                    ),
                                    onSelected = { callbacks.onAccountSelected(account.address) }
                                )
                            }
                        }
                    }
                    IrohaConnectPhase.CONNECTED -> AttentionMessage(message = stringResource(R.string.iroha_connect_waiting_requests))
                    IrohaConnectPhase.REQUEST_APPROVAL -> {
                        state.requestTitle?.let { InfoItem(state = InfoItemViewState(title = stringResource(it), subtitle = null)) }
                        state.requestSections.forEach { Section(it) }
                    }
                    else -> Unit
                }
            }
            Buttons(state, callbacks)
        }
    }
}

@Composable
private fun Buttons(state: IrohaConnectViewState, callbacks: IrohaConnectScreenCallbacks) {
    val buttons = when (state.phase) {
        IrohaConnectPhase.SESSION_APPROVAL -> Quad(
            R.string.connection_approve.takeIf { state.accounts.isNotEmpty() },
            callbacks::onApproveSession,
            R.string.connection_reject,
            callbacks::onRejectSession
        )
        IrohaConnectPhase.REQUEST_APPROVAL -> Quad(R.string.common_sign, callbacks::onApproveRequest, R.string.connection_reject, callbacks::onRejectRequest)
        IrohaConnectPhase.CONNECTING, IrohaConnectPhase.CONNECTED -> Quad(null, callbacks::onClose, R.string.common_disconnect, callbacks::onDisconnect)
        IrohaConnectPhase.IDLE, IrohaConnectPhase.ERROR -> Quad(null, callbacks::onClose, R.string.common_close, callbacks::onClose)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .wrapContentHeight()
    ) {
        MarginVertical(margin = 8.dp)
        buttons.accent?.let {
            AccentButton(
                text = stringResource(it),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 16.dp),
                enabled = !state.busy,
                loading = state.busy,
                onClick = buttons.accentAction
            )
            MarginVertical(margin = 8.dp)
        }
        GrayButton(
            text = stringResource(buttons.gray),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp),
            enabled = !state.busy,
            onClick = buttons.grayAction
        )
        MarginVertical(margin = 16.dp)
    }
}

private data class Quad(val accent: Int?, val accentAction: () -> Unit, val gray: Int, val grayAction: () -> Unit)

@Preview
@Composable
private fun IrohaConnectPreview() {
    FearlessTheme {
        IrohaConnectContent(
            state = IrohaConnectViewState(
                phase = IrohaConnectPhase.SESSION_APPROVAL,
                appName = "Iroha dApp",
                appUrl = "https://example.org",
                sessionRows = listOf(IrohaConnectRow(R.string.common_network, "SORA Taira")),
                accounts = listOf(IrohaConnectAccountItem("testu…", "Main", true))
            ),
            callbacks = object : IrohaConnectScreenCallbacks {
                override fun onAccountSelected(address: String) = Unit
                override fun onApproveSession() = Unit
                override fun onRejectSession() = Unit
                override fun onApproveRequest() = Unit
                override fun onRejectRequest() = Unit
                override fun onDisconnect() = Unit
                override fun onDismissMessage() = Unit
                override fun onClose() = Unit
            }
        )
    }
}
