package jp.co.soramitsu.tonconnect.impl.presentation.connections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.jp.soramitsu.tonconnect.model.DappModel
import coil.compose.AsyncImage
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.B2
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.CorneredInput
import jp.co.soramitsu.common.compose.component.GradientIcon
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.component.H4
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.getImageRequest
import jp.co.soramitsu.common.compose.theme.alertYellow
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.black4
import jp.co.soramitsu.common.compose.theme.white50

data class TonConnectionsScreenViewState(
    val items: List<DappModel>,
    val searchQuery: String? = null
) {
    companion object {
        val default = TonConnectionsScreenViewState(emptyList())
    }
}

@Composable
fun TonConnectionsContent(
    state: TonConnectionsScreenViewState,
    onDappClick: (dappItem: DappModel) -> Unit = {},
    onSearchInput: (input: String) -> Unit = {},
    onClose: () -> Unit = {},
    onCreateNewConnection: () -> Unit = {}
) {
    BottomSheetScreen(
        modifier = Modifier
            .padding(top = 106.dp)
    ) {
        Toolbar(state = ToolbarViewState(stringResource(id = R.string.connection_connections), R.drawable.ic_arrow_back_24dp), onNavigationClick = onClose)

        MarginVertical(margin = 16.dp)
        CorneredInput(
            modifier = Modifier.padding(horizontal = 16.dp),
            state = state.searchQuery,
            onInput = onSearchInput,
            hintLabel = stringResource(id = R.string.connections_search_hint)
        )

        Column(
            modifier = Modifier
                .nestedScroll(rememberNestedScrollInteropConnection())
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
        ) {
            when {
                state.items.isEmpty() && state.searchQuery.isNullOrEmpty() -> {
                    Spacer(Modifier.weight(1f))
                }

                state.items.isEmpty() -> {
                    MarginVertical(margin = 16.dp)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .align(Alignment.CenterHorizontally)
                    ) {
                        EmptyResultContent()
                    }
                }

                else -> {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(state.items) { chain ->
                            ConnectionItem(
                                dapp = chain,
                                onClicked = onDappClick
                            )
                        }
                    }
                }
            }
            AccentButton(
                text = stringResource(id = R.string.connection_create_new),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                onClick = onCreateNewConnection
            )
            MarginVertical(margin = 16.dp)
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
            text = stringResource(id = R.string.common_empty_search),
            color = white50
        )
    }
}

@Composable
fun ConnectionItem(dapp: DappModel, onClicked: (DappModel) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .defaultMinSize(minHeight = 56.dp)
            .fillMaxWidth()
            .clickable { onClicked(dapp) }
    ) {
        if (dapp.icon != null) {
            AsyncImage(
                model = getImageRequest(LocalContext.current, dapp.icon!!),
                contentDescription = null,
                modifier = Modifier
                    .clip(CircleShape)
                    .testTag("ConnectionItem_image_${dapp.name}")
                    .size(24.dp)
            )
        } else {
            Image(
                res = R.drawable.ic_dapp_connection,
                modifier = Modifier
                    .testTag("ConnectionItem_image_placeholder")
                    .size(24.dp)
            )
        }

        MarginHorizontal(margin = 5.dp)

        Column(modifier = Modifier.weight(1f)) {
            H4(text = dapp.name.orEmpty(), color = black2)
            dapp.url?.let { B2(text = dapp.url!!) }
        }

        Image(
            res = R.drawable.ic_right_arrow_24_align_right,
            modifier = Modifier
                .testTag("ChainItem_right_arrow")
                .size(24.dp)
        )
    }
}

@Preview
@Composable
private fun TonConnectionsPreview() {
    val items = listOf(
        DappModel(
            identifier = "",
            url = "",
            chains = listOf(),
            name = "Kusama",
            description = null,
            background = null,
            icon = "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Moonriver.svg",
            metaId = null
        ),
        DappModel(
            identifier = "2",
            url = "https://ton-connect.example.fearless.soramitsu.co.jp",
            chains = listOf(),
            name = "Fearless",
            description = "Fearless DeFi dapp for TON",
            background = null,
            icon = "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Moonriver.svg",
            metaId = null
        ),
    )
    val state = TonConnectionsScreenViewState(
        items = items,
        searchQuery = null
    )
    Column(
        Modifier.background(black4)
    ) {
        TonConnectionsContent(state = state)
    }
}
