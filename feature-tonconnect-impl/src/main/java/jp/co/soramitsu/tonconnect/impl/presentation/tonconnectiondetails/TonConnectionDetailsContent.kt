package jp.co.soramitsu.tonconnect.impl.presentation.tonconnectiondetails

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
import co.jp.soramitsu.tonconnect.model.AppEntity
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
import jp.co.soramitsu.common.compose.component.WalletNameItem
import jp.co.soramitsu.common.compose.component.WalletNameItemViewState

data class TonConnectionDetailsViewState(
    val appInfo: AppEntity?,
    val reviewDappInfo: InfoItemSetViewState?,
    val requiredNetworksSelectorState: SelectorState?,
    val wallets: List<WalletNameItemViewState>,
    val approving: Boolean,
    val rejecting: Boolean
) {

    companion object {
        val default = TonConnectionDetailsViewState(
            appInfo = null,
            reviewDappInfo = null,
            requiredNetworksSelectorState = null,
            wallets = listOf(),
            approving = false,
            rejecting = false
        )
    }
}

interface TonConnectionDetailsScreenInterface {
    fun onClose()
    fun onApproveClick()
    fun onRejectClicked()
    fun onWalletSelected(item: WalletNameItemViewState)
}

@Composable
fun TonConnectionDetailsContent(state: TonConnectionDetailsViewState, callback: TonConnectionDetailsScreenInterface) {
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
                        title = state.appInfo?.name,
                        subtitle = state.appInfo?.url,
                        imageUrl = state.appInfo?.iconUrl,
                        placeholderIcon = R.drawable.ic_dapp_connection
                    )
                )

                state.requiredNetworksSelectorState?.let {
                    SelectorWithBorder(
                        state = it
                    )
                }

                state.reviewDappInfo?.let {
                    CollapsibleView(title = stringResource(id = R.string.tc_review_dapp_info)) {
                        InfoItemSet(state = it)
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.wallets.map { walletItemState ->
                        WalletNameItem(
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
private fun TonConnectionDetailsPreview() {
    val state = TonConnectionDetailsViewState(
        appInfo = AppEntity(
            url = "ton-org.github.io",
            name = "blueprint",
            iconUrl = "",
            termsOfUseUrl = null,
            privacyPolicyUrl = null
        ),
        reviewDappInfo = InfoItemSetViewState(
            title = "Review dApp info",
            infoItems = listOf(
                InfoItemViewState(
                    title = "Methods",
                    subtitle = "ton-org.github.io"
                )
            )
        ),
        requiredNetworksSelectorState = SelectorState(
            title = "Required networks",
            subTitle = "Telegram mainnet",
            iconUrl = null
        ),
        wallets = listOf(),
        approving = false,
        rejecting = false
    )

    val emptyCallback = object : TonConnectionDetailsScreenInterface {
        override fun onClose() {}
        override fun onApproveClick() {}
        override fun onRejectClicked() {}
        override fun onWalletSelected(item: WalletNameItemViewState) {}
    }

    TonConnectionDetailsContent(
        state = state,
        callback = emptyCallback
    )
}
