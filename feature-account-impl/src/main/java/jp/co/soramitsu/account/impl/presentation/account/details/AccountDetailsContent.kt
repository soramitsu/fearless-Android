package jp.co.soramitsu.account.impl.presentation.account.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.account.api.presentation.importing.ImportAccountType
import jp.co.soramitsu.account.impl.presentation.account.model.ConnectedAccountsInfoItem
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.B1
import jp.co.soramitsu.common.compose.component.BackgroundCorneredWithBorder
import jp.co.soramitsu.common.compose.component.H5
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.WalletItem
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.alertYellow
import jp.co.soramitsu.common.compose.theme.black05
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white08
import jp.co.soramitsu.common.compose.theme.white30
import jp.co.soramitsu.common.utils.clickableSingle

data class AccountDetailsState(
    val walletItem: WalletItemViewState?,
    val connectedAccountsInfo: List<ConnectedAccountsInfoItem>,
) {
    companion object {
        val Empty = AccountDetailsState(
            walletItem = null,
            connectedAccountsInfo = emptyList()
        )
    }
}

interface AccountDetailsCallback {

    fun onBackClick()

    fun accountsItemOptionsClicked(type: ImportAccountType)

    fun walletOptionsClicked(item: WalletItemViewState)
}

@Composable
internal fun AccountDetailsContent(
    state: AccountDetailsState,
    callback: AccountDetailsCallback
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(rememberNestedScrollInteropConnection())
    ) {
        Toolbar(
            modifier = Modifier.padding(bottom = 12.dp),
            state = ToolbarViewState(
                title = stringResource(R.string.common_details_wallet).lowercase().replaceFirstChar { it.titlecase() },
                navigationIcon = R.drawable.ic_arrow_back_24dp
            ),
            onNavigationClick = callback::onBackClick
        )
        if (state.walletItem != null) {
            MarginVertical(16.dp)
            WalletItem(
                modifier = Modifier
                    .padding(horizontal = 16.dp),
                state = state.walletItem,
                onOptionsClick = callback::walletOptionsClicked,
                onSelected = {}
            )
        }
        MarginVertical(4.dp)

        BackgroundCorneredWithBorder(
            modifier = Modifier
                .padding(horizontal = 16.dp),
            borderColor = white30,
            backgroundColor = black05
        ) {
            Column {
                H5(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    text = "Connected accounts"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .height(1.dp)
                        .background(white08)
                )
                state.connectedAccountsInfo.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        B1(text = item.title)
                        Spacer(modifier = Modifier.weight(1f))
                        if (item.amount > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                B1(text = item.amount.toString())
                                MarginHorizontal(10.dp)
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .minimumInteractiveComponentSize()
                                        .clickableSingle {
                                            callback.accountsItemOptionsClicked(item.accountType)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_dots_horizontal_24),
                                        tint = white,
                                        contentDescription = null
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .minimumInteractiveComponentSize()
                                    .clickableSingle {
                                        callback.accountsItemOptionsClicked(item.accountType)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    modifier = Modifier.size(24.dp),
                                    painter = painterResource(id = R.drawable.ic_alert_16),
                                    tint = alertYellow,
                                    contentDescription = null
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@Preview
@Composable
private fun PreviewAccountDetailsContent() {
    val state = AccountDetailsState(
        walletItem = WalletItemViewState(0L, null, "SMBL", null, "Title", "", false),
        connectedAccountsInfo = listOf(
            ConnectedAccountsInfoItem(
                ImportAccountType.Substrate,
                "Substrate chain accounts",
                33
            ),
            ConnectedAccountsInfoItem(
                ImportAccountType.Ethereum,
                "EVM chain accounts",
                0
            )
        )
    )
    FearlessAppTheme {
        AccountDetailsContent(
            state = state,
            callback = object : AccountDetailsCallback {
                override fun onBackClick() {}
                override fun accountsItemOptionsClicked(type: ImportAccountType) {}
                override fun walletOptionsClicked(item: WalletItemViewState) {}
            }
        )
    }
}
