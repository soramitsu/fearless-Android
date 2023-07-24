package jp.co.soramitsu.account.impl.presentation.account.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.views.CompactWalletItemViewState
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.CapsTitle2
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.compose.component.SelectorWithBorder
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.WalletItem
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.list.headers.TextHeader

data class AccountDetailsState(
    val walletItem: WalletItemViewState?,
    val chainProjections: List<Any?>
) {
    companion object {
        val Empty = AccountDetailsState(
            walletItem = null,
            chainProjections = emptyList()
        )
    }
}

interface AccountDetailsCallback {

    fun onBackClick()

    fun chainAccountOptionsClicked(item: AccountInChainUi)
}

@Composable
internal fun AccountDetailsContent(
    state: AccountDetailsState,
    callback: AccountDetailsCallback
) {
    Column {
        Toolbar(
            modifier = Modifier.padding(bottom = 12.dp),
            state = ToolbarViewState(
                title = stringResource(R.string.common_title_wallet),
                navigationIcon = R.drawable.ic_arrow_back_24dp
            ),
            onNavigationClick = callback::onBackClick
        )
        if (state.walletItem != null) {
            MarginVertical(16.dp)
            WalletItem(
                modifier = Modifier
                    .padding(horizontal = 16.dp),
                state = CompactWalletItemViewState(
                    title = state.walletItem.title
                ),
                onSelected = {}
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.chainProjections) { item ->
                when (item) {
                    is TextHeader -> {
                        Box(
                            modifier = Modifier.height(32.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            CapsTitle2(
                                text = item.content
                            )
                        }
                    }

                    is AccountInChainUi -> {
                        SelectorWithBorder(
                            state = SelectorState(
                                title = item.chainName,
                                subTitle = item.address,
                                iconUrl = item.chainIcon,
                                actionIcon = R.drawable.ic_dots_horizontal_24
                            ),
                            onClick = {
                                callback.chainAccountOptionsClicked(item)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewAccountDetailsContent() {
    val chainUi = AccountInChainUi("", "Chain name", "", "AddressAddressadrressaddressaddress", null, true, "accountName", null, true, true, false)
    val state = AccountDetailsState(
        walletItem = WalletItemViewState(0L, null, "SMBL", null, "Title", "", false),
        chainProjections = listOf(
            TextHeader("ACCOUNTS WITH A SHARED SECRET"),
            chainUi
        )
    )
    FearlessAppTheme {
        AccountDetailsContent(state = state, callback = object : AccountDetailsCallback {
            override fun onBackClick() {}
            override fun chainAccountOptionsClicked(item: AccountInChainUi) {}
        })
    }
}
