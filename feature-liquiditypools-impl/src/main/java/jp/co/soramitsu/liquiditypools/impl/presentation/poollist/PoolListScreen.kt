package jp.co.soramitsu.liquiditypools.impl.presentation.poollist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.common.compose.component.CorneredInput
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.theme.white04
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.liquiditypools.impl.presentation.allpools.BasicPoolListItem
import jp.co.soramitsu.liquiditypools.impl.presentation.allpools.BasicPoolListItemState
import jp.co.soramitsu.ui_core.resources.Dimens

data class PoolListState(
    val pools: List<BasicPoolListItemState> = listOf(),
    val searchQuery: String? = null
)

interface PoolListScreenInterface {
    fun onPoolClicked(pair: StringPair)
    fun onAssetSearchEntered(value: String)

}

@Composable
fun PoolListScreen(
    state: PoolListState,
    callback: PoolListScreenInterface
) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        MarginVertical(margin = 16.dp)
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            CorneredInput(
                state = state.searchQuery,
                borderColor = white04,
                hintLabel = stringResource(id = R.string.manage_assets_search_hint),
                onInput = callback::onAssetSearchEntered
            )
        }

        val listState = rememberLazyListState()

        LazyColumn(
            state = listState,
            modifier = Modifier
                .wrapContentHeight()
        ) {
            items(state.pools) { pool ->
                BasicPoolListItem(
                    modifier = Modifier.padding(vertical = Dimens.x1),
                    state = pool,
                    onPoolClick = callback::onPoolClicked,
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewPoolListScreen() {
    val itemState = BasicPoolListItemState(
        ids = "0" to "1",
        token1Icon = "DEFAULT_ICON_URI",
        token2Icon = "DEFAULT_ICON_URI",
        text1 = "XOR-VAL",
        text2 = "123.4M",
        text3 = "1234.3%",
        text4 = "Earn SWAP",
    )

    val items = listOf(
        itemState,
        itemState.copy(text1 = "TEXT1", text2 = "TEXT2", text3 = "TEXT3", text4 = "TEXT4"),
        itemState.copy(text1 = "text1", text2 = "text2", text3 = "text3", text4 = "text4"),
    )
    PoolListScreen(
        state = PoolListState(
            pools = items,
        ),
        callback = object : PoolListScreenInterface {
            override fun onPoolClicked(pair: StringPair) {}
            override fun onAssetSearchEntered(value: String) {}
        },
    )
}
