package jp.co.soramitsu.walletconnect.impl.presentation.connections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import jp.co.soramitsu.common.utils.withNoFontPadding


data class ConnectionsScreenViewState(
    val items: List<SessionItemState>,
    val searchQuery: String? = null
) {
    companion object {
        val default = ConnectionsScreenViewState(emptyList())
    }
}

@Composable
fun ConnectionsContent(
    state: ConnectionsScreenViewState,
    onSessionClick: (sessionItemState: SessionItemState) -> Unit = {},
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
                .imePadding()
        ) {
            when {
                state.items.isEmpty() && state.searchQuery.isNullOrEmpty() -> {
                    Spacer(Modifier.weight(1f))
                }
                state.items.isEmpty() -> {
                    MarginVertical(margin = 16.dp)
                    Column(
                        horizontalAlignment = CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .align(CenterHorizontally)
                    ) {
                        EmptyResultContent()
                    }
                }

                else -> {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(state.items) { chain ->
                            SessionItem(
                                state = chain,
                                onClicked = onSessionClick
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

data class SessionItemState(
    val topic: String,
    val title: String,
    val url: String?,
    val imageUrl: String?
)

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
            modifier = Modifier.align(CenterHorizontally),
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
fun SessionItem(
    state: SessionItemState,
    onClicked: (SessionItemState) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(56.dp)
            .fillMaxWidth()
            .clickable { onClicked(state) }
    ) {
        if (state.imageUrl != null) {
            AsyncImage(
                model = getImageRequest(LocalContext.current, state.imageUrl),
                contentDescription = null,
                modifier = Modifier
                    .clip(CircleShape)
                    .testTag("ConnectionItem_image_${state.title}")
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
            H4(text = state.title, color = black2)
            state.url?.let { B2(text = state.url) }
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
private fun SelectChainScreenPreview() {
    val items = listOf(
        SessionItemState(
            topic = "1",
            imageUrl = "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Moonriver.svg",
            title = "Kusama",
            url = ""
        ),
        SessionItemState(
            topic = "2",
            imageUrl = "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Kusama.svg",
            title = "Moonriver",
            url = ""
        )
    )
    val state = ConnectionsScreenViewState(
        items = emptyList(),// items,
        searchQuery = null
    )
    Column(
        Modifier.background(black4)
    ) {
        ConnectionsContent(state = state)
    }
}
