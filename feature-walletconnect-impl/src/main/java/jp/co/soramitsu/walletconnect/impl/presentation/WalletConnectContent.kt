package jp.co.soramitsu.walletconnect.impl.presentation

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
import com.walletconnect.web3.wallet.client.Wallet
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.CollapsibleView
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.InfoItem
import jp.co.soramitsu.common.compose.component.InfoItemSet
import jp.co.soramitsu.common.compose.component.InfoItemSetViewState
import jp.co.soramitsu.common.compose.component.InfoItemViewState
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.compose.component.SelectorWithBorder
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.WalletItem
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme

data class WalletConnectViewState(
    val sessionProposal: Wallet.Model.SessionProposal?,
    val requiredPermissions: InfoItemSetViewState?,
    val optionalPermissions: InfoItemSetViewState?,
    val requiredNetworksSelectorState: SelectorState?,
    val optionalNetworksSelectorState: SelectorState?,
    val wallets: List<WalletItemViewState>,
    val approving: Boolean,
    val rejecting: Boolean
) {

    companion object {
        val default = WalletConnectViewState(
            sessionProposal = null,
            requiredPermissions = null,
            optionalPermissions = null,
            requiredNetworksSelectorState = null,
            optionalNetworksSelectorState = null,
            wallets = listOf(),
            approving = false,
            rejecting = false
        )
    }
}

interface WalletConnectScreenInterface {
    fun onClose()
    fun onApproveClick()
    fun onRejectClicked()
    fun onOptionalNetworksClicked()
    fun onRequiredNetworksClicked()
    fun onWalletSelected(item: WalletItemViewState)
}

@Composable
fun WalletConnectContent(state: WalletConnectViewState, callback: WalletConnectScreenInterface) {
    BottomSheetScreen {
        Column(modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection())) {
            Toolbar(
                state = ToolbarViewState(
                    title = stringResource(id = R.string.connection_details_title),
                    navigationIcon = R.drawable.ic_arrow_back_24dp
                ),
                onNavigationClick = callback::onClose
            )
            MarginVertical(margin = 16.dp)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoItem(
                    state = InfoItemViewState(
                        title = state.sessionProposal?.name,
                        subtitle = state.sessionProposal?.url,
                        imageUrl = state.sessionProposal?.icons?.firstOrNull()?.toString(),
                        placeholderIcon = R.drawable.ic_dapp_connection
                    )
                )

                state.requiredNetworksSelectorState?.let {
                    SelectorWithBorder(
                        state = it,
                        onClick = callback::onRequiredNetworksClicked
                    )
                }

                state.requiredPermissions?.let {
                    CollapsibleView(title = stringResource(id = R.string.connection_review_required_permissions)) {
                        InfoItemSet(state = it)
                    }
                }

                state.optionalNetworksSelectorState?.let {
                    SelectorWithBorder(
                        state = it,
                        onClick = callback::onOptionalNetworksClicked
                    )
                }

                state.optionalPermissions?.let {
                    CollapsibleView(title = stringResource(id = R.string.connection_review_optional_permissions)) {
                        InfoItemSet(state = it)
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.wallets.map { walletItemState ->
                        WalletItem(
                            state = walletItemState,
                            onSelected = callback::onWalletSelected
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .wrapContentHeight()
            ) {
                MarginVertical(margin = 8.dp)
                AccentButton(
                    text = stringResource(id = R.string.connection_approve),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 16.dp),
                    enabled = state.rejecting.not(),
                    loading = state.approving,
                    onClick = callback::onApproveClick
                )

                MarginVertical(margin = 8.dp)
                GrayButton(
                    text = stringResource(id = R.string.connection_reject),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 16.dp),
                    enabled = state.approving.not(),
                    loading = state.rejecting,
                    onClick = callback::onRejectClicked
                )

                MarginVertical(margin = 16.dp)
            }
        }
    }
}

@Preview
@Composable
private fun WalletConnectPreview() {
    val state = WalletConnectViewState(
        sessionProposal = null,
        requiredPermissions = InfoItemSetViewState(
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
        optionalPermissions = InfoItemSetViewState(
            title = "Optional chain name",
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
        requiredNetworksSelectorState = SelectorState(
            title = "Required networks",
            subTitle = "Soma Network Name, Twoma Chain",
            iconUrl = null
        ),
        optionalNetworksSelectorState = SelectorState(
            title = "Optional networks",
            subTitle = "Soma Network Name",
            iconUrl = null
        ),
        wallets = listOf(),
        approving = false,
        rejecting = false
    )

    val emptyCallback = object : WalletConnectScreenInterface {
        override fun onClose() {}
        override fun onApproveClick() {}
        override fun onRejectClicked() {}
        override fun onRequiredNetworksClicked() {}
        override fun onOptionalNetworksClicked() {}
        override fun onWalletSelected(item: WalletItemViewState) {}
    }

    FearlessTheme {
        WalletConnectContent(
            state = state,
            callback = emptyCallback
        )
    }
}
