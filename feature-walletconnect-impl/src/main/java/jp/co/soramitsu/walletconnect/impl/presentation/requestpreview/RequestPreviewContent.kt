package jp.co.soramitsu.walletconnect.impl.presentation.requestpreview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.GradientIcon
import jp.co.soramitsu.common.compose.component.GradientIconState
import jp.co.soramitsu.common.compose.component.InfoTable
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.ToolbarBottomSheet
import jp.co.soramitsu.common.compose.component.WalletNameItem
import jp.co.soramitsu.common.compose.component.WalletNameItemViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.walletconnect.impl.presentation.WalletConnectMethod

data class RequestPreviewViewState(
    val chainIcon: GradientIconState,
    val method: String?,
    val tableItems: List<TitleValueViewState>,
    val wallet: WalletNameItemViewState,
    val loading: Boolean
) {
    companion object {
        val default = RequestPreviewViewState(
            chainIcon = GradientIconState.Local(R.drawable.ic_fearless_logo),
            method = null,
            tableItems = listOf(),
            wallet = WalletNameItemViewState(
                id = 0,
                title = "",
                walletIcon = R.drawable.ic_wallet,
                isSelected = false
            ),
            loading = false
        )
    }
}

interface RequestPreviewScreenInterface {
    fun onClose()
    fun onSignClick()
    fun onTableItemClick(id: Int)
    fun onTableRowClick(id: Int)
}

@Composable
fun RequestPreviewContent(state: RequestPreviewViewState, callback: RequestPreviewScreenInterface) {
    BottomSheetScreen {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .nestedScroll(rememberNestedScrollInteropConnection())
        ) {
            ToolbarBottomSheet(
                title = stringResource(id = R.string.common_preview),
                onNavigationClick = callback::onClose
            )

            MarginVertical(margin = 8.dp)

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
            ) {
                GradientIcon(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    icon = state.chainIcon,
                    color = colorAccentDark
                )
                MarginVertical(margin = 16.dp)

                state.method?.let {
                    Text(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        text = state.method,
                        style = MaterialTheme.customTypography.header3,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
                MarginVertical(margin = 24.dp)

                WalletNameItem(
                    state = state.wallet,
                    onSelected = {}
                )
                MarginVertical(margin = 16.dp)

                InfoTable(
                    items = state.tableItems,
                    onItemClick = callback::onTableItemClick,
                    onRowClick = callback::onTableRowClick
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .wrapContentHeight()
            ) {
                MarginVertical(margin = 12.dp)

                AccentButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    text = stringResource(id = R.string.common_sign),
                    loading = state.loading,
                    onClick = callback::onSignClick
                )

                MarginVertical(margin = 16.dp)
            }
        }
    }
}

@Preview
@Composable
private fun RequestPreviewPreview() {
    val tableItems = listOf(
        TitleValueViewState(
            "dApp",
            "React App"
        ),
        TitleValueViewState(
            "Host",
            "react-app.walletconnect.com"
        ),
        TitleValueViewState(
            "Network",
            "Ethereum Goerli"
        ),
        TitleValueViewState(
            "Transaction raw data",
            value = "",
            clickState = TitleValueViewState.ClickState.Value(R.drawable.ic_right_arrow_24_align_right, 1)
        )
    )

    val state = RequestPreviewViewState(
        chainIcon = GradientIconState.Local(R.drawable.ic_fearless_logo),
        method = WalletConnectMethod.PolkadotSignMessage.method,
        tableItems = tableItems,
        wallet = WalletNameItemViewState(
            id = 1,
            title = "Wallet",
            walletIcon = R.drawable.ic_wallet,
            isSelected = false
        ),
        loading = false
    )

    val emptyCallback = object : RequestPreviewScreenInterface {
        override fun onClose() {}
        override fun onSignClick() {}
        override fun onTableItemClick(id: Int) {}
        override fun onTableRowClick(id: Int) {}
    }

    FearlessTheme {
        RequestPreviewContent(
            state = state,
            callback = emptyCallback
        )
    }
}
