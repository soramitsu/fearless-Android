package jp.co.soramitsu.liquiditypools.impl.presentation.allpools

import android.graphics.Paint.Align
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.common.R.drawable
import jp.co.soramitsu.common.compose.component.BackgroundCorneredWithBorder
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.NavigationIconButton
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.red
import jp.co.soramitsu.common.compose.theme.transparent
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white08
import jp.co.soramitsu.common.utils.clickableWithNoIndication
import jp.co.soramitsu.feature_liquiditypools_impl.R
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors

data class AllPoolsState(
    val pools: List<BasicPoolListItemState> = listOf()
)

interface AllPoolsScreenInterface {
    fun onPoolClicked(pair: StringPair)
    fun onNavigationClick()
    fun onCloseClick()
    fun onMoreClick()
}

@Composable
fun AllPoolsScreen(
    state: AllPoolsState,
    callback: AllPoolsScreenInterface
) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Toolbar(callback)

        val listState = rememberLazyListState()

        BackgroundCorneredWithBorder(
            modifier = Modifier
                .padding(horizontal = 16.dp)
        ) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier
                    .wrapContentHeight()
            ) {
                item {
                    PoolGroupHeader(callback)
                }
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
}

@Composable
private fun PoolGroupHeader(callback: AllPoolsScreenInterface) {
    Box(modifier = Modifier.wrapContentHeight()) {
        Row(
            modifier = Modifier
                .padding(vertical = Dimens.x1_5, horizontal = Dimens.x1_5)
                .wrapContentHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.wrapContentHeight(),
                color = white,
                style = MaterialTheme.customTypography.header5,
                text = "Your pools",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .background(
                        color = white08,
                        shape = CircleShape,
                    )
                    .padding(all = Dimens.x1)
            ) {
                Text(
                    text = "MORE",
                    modifier = Modifier
                        .align(Alignment.CenterVertically),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.customTypography.capsTitle2,
                    color = white,
                )
                Image(
                    res = R.drawable.ic_chevron_right,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .align(Alignment.CenterVertically)
                        .clickableWithNoIndication(callback::onMoreClick)
                )
            }
        }
        Box(
            modifier = Modifier
                .height(1.dp)
                .padding(horizontal = Dimens.x1_5)
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(white08)
        )
    }
}

@Composable
private fun Toolbar(callback: AllPoolsScreenInterface) {
    Row(
        modifier = Modifier
            .wrapContentHeight()
            .padding(bottom = 12.dp)
    ) {
        NavigationIconButton(
            modifier = Modifier.padding(start = 16.dp),
            onNavigationClick = callback::onNavigationClick
        )

        Image(
            modifier = Modifier
                .padding(start = 8.dp)
                .align(Alignment.Top)
                .size(
                    width = 100.dp,
                    height = 28.dp
                ),
            res = R.drawable.logo_polkaswap_big,
            contentDescription = null
        )
        Spacer(modifier = Modifier.weight(1f))
        NavigationIconButton(
            modifier = Modifier
                .align(Alignment.Top)
                .padding(end = 16.dp),
            navigationIconResId = drawable.ic_cross_32,
            onNavigationClick = callback::onCloseClick
        )
    }
}

@Preview
@Composable
private fun PreviewAllPoolsInternal() {
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
    Column {
        BottomSheetScreen {
            AllPoolsScreen(
                state = AllPoolsState(
                    pools = items,
                ),
                callback = object : AllPoolsScreenInterface {
                    override fun onPoolClicked(pair: StringPair) {}
                    override fun onNavigationClick() {}
                    override fun onCloseClick() {}
                    override fun onMoreClick() {}
                },
            )
        }
    }
}
