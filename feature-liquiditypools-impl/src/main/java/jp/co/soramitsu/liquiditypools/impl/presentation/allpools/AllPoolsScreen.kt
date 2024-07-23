package jp.co.soramitsu.liquiditypools.impl.presentation.allpools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.common.compose.component.BackgroundCorneredWithBorder
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white08
import jp.co.soramitsu.feature_liquiditypools_impl.R
import jp.co.soramitsu.ui_core.resources.Dimens

data class AllPoolsState(
    val userPools: List<BasicPoolListItemState> = listOf(),
    val allPools: List<BasicPoolListItemState> = listOf(),
    val hasExtraUserPools: Boolean = false,
    val hasExtraAllPools: Boolean = false,
)

interface AllPoolsScreenInterface {
    fun onPoolClicked(pair: StringPair)
    fun onMoreClick(isUserPools: Boolean)
}


@Composable
fun AllPoolsScreen(
    state: AllPoolsState,
    callback: AllPoolsScreenInterface
) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        MarginVertical(margin = 16.dp)

        if (state.userPools.isNotEmpty()) {
            BackgroundCorneredWithBorder(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier
                        .wrapContentHeight()
                ) {
                    PoolGroupHeader(
                        title = stringResource(id = R.string.pl_your_pools),
                        onMoreClick = { callback.onMoreClick(true) }.takeIf { state.hasExtraUserPools }
                    )
                    state.userPools.forEach { pool ->
                        BasicPoolListItem(
                            modifier = Modifier.padding(vertical = Dimens.x1),
                            state = pool,
                            onPoolClick = callback::onPoolClicked,
                        )
                    }
                }
            }
            MarginVertical(margin = 16.dp)
        }
        if (state.allPools.isNotEmpty()) {
            BackgroundCorneredWithBorder(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier.wrapContentHeight()
                ) {
                    PoolGroupHeader(
                        title = stringResource(id = R.string.pl_all_pools),
                        onMoreClick = { callback.onMoreClick(false) }.takeIf { state.hasExtraAllPools }
                    )
                    state.allPools.forEach { pool ->
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
}

@Composable
private fun PoolGroupHeader(title: String, onMoreClick: (() -> Unit)?) {
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
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            onMoreClick?.let {
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
                            .clickable(onClick = onMoreClick)
                    )
                }
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
    AllPoolsScreen(
        state = AllPoolsState(
            userPools = items,
            allPools = items,
        ),
        callback = object : AllPoolsScreenInterface {
            override fun onPoolClicked(pair: StringPair) {}
            override fun onMoreClick(isUserPools: Boolean) {}
        },
    )
}
