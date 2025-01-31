package jp.co.soramitsu.tonconnect.impl.presentation.connectioninfo

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
import jp.co.soramitsu.common.compose.component.InfoItem
import jp.co.soramitsu.common.compose.component.InfoItemViewState
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.compose.component.SelectorWithBorder
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.WalletNameItem
import jp.co.soramitsu.common.compose.component.WalletNameItemViewState
import jp.co.soramitsu.tonconnect.api.model.DappModel

data class TonConnectionInfoViewState(
    val appInfo: DappModel?,
    val requiredNetworksSelectorState: SelectorState?,
    val wallet: WalletNameItemViewState?
) {

    companion object {
        val default = TonConnectionInfoViewState(
            appInfo = null,
            requiredNetworksSelectorState = null,
            wallet = null
        )
    }
}

interface TonConnectionInfoScreenInterface {
    fun onClose()
    fun onDisconnectClick()
}

@Composable
fun TonConnectionInfoContent(state: TonConnectionInfoViewState, callback: TonConnectionInfoScreenInterface) {
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
                        imageUrl = state.appInfo?.icon,
                        placeholderIcon = R.drawable.ic_dapp_connection
                    )
                )

                state.requiredNetworksSelectorState?.let {
                    SelectorWithBorder(
                        state = it
                    )
                }

                state.wallet?.let { walletItemState ->
                    WalletNameItem(
                        state = walletItemState,
                        onSelected = {}
                    )
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
private fun TonConnectionInfoPreview() {
    val state = TonConnectionInfoViewState(
        appInfo = DappModel(
            identifier = "",
            url = "ton-org.github.io",
            name = "blueprint",
            icon = "",
            chains = listOf(),
            description = null,
            background = null,
            metaId = 1
        ),
        requiredNetworksSelectorState = SelectorState(
            title = "Required networks",
            subTitle = "Telegram mainnet",
            iconUrl = null
        ),
        wallet = null
    )

    val emptyCallback = object : TonConnectionInfoScreenInterface {
        override fun onClose() {}
        override fun onDisconnectClick() {}
    }

    TonConnectionInfoContent(
        state = state,
        callback = emptyCallback
    )
}
