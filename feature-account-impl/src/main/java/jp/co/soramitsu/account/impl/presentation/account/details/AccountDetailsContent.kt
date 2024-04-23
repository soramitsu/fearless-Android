package jp.co.soramitsu.account.impl.presentation.account.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.CapsTitle2
import jp.co.soramitsu.common.compose.component.CorneredInput
import jp.co.soramitsu.common.compose.component.GradientIcon
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.compose.component.SelectorWithBorder
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.WalletItem
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.alertYellow
import jp.co.soramitsu.common.compose.theme.black05
import jp.co.soramitsu.common.compose.theme.white24
import jp.co.soramitsu.common.compose.theme.white50
import jp.co.soramitsu.common.list.headers.TextHeader

data class AccountDetailsState(
    val walletItem: WalletItemViewState?,
    val chainProjections: List<Any?>,
    val searchQuery: String? = null
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

    fun onSearchInput(input: String)
}

@Composable
internal fun AccountDetailsContent(
    state: AccountDetailsState,
    callback: AccountDetailsCallback
) {
    Column(
        modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection())
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
                onSelected = {}
            )
        }
        MarginVertical(4.dp)

        CorneredInput(
            modifier = Modifier.padding(horizontal = 16.dp),
            textModifier = Modifier.height(48.dp),
            backgroundColor = black05,
            borderColor = white24,
            state = state.searchQuery,
            onInput = callback::onSearchInput,
            hintLabel = stringResource(id = R.string.search_network_hint)
        )
        MarginVertical(4.dp)

        if (state.searchQuery != null && state.chainProjections.isEmpty()) {
            MarginVertical(margin = 20.dp)
            EmptyResultContent()
        } else {
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
                                    actionIcon = R.drawable.ic_dots_horizontal_24,
                                    enabled = item.enabled,
                                    subTitleIcon = R.drawable.ic_alert_16.takeIf { item.hasAccount.not() }
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
}

@Composable
fun EmptyResultContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GradientIcon(
            iconRes = R.drawable.ic_alert_24,
            color = alertYellow,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(bottom = 4.dp)
        )

        H3(text = stringResource(id = R.string.common_search_assets_alert_title))
        B0(
            text = stringResource(id = R.string.accounts_not_found),
            color = white50
        )
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
        AccountDetailsContent(
            state = state,
            callback = object : AccountDetailsCallback {
                override fun onBackClick() {}
                override fun chainAccountOptionsClicked(item: AccountInChainUi) {}
                override fun onSearchInput(input: String) {}
            }
        )
    }
}
