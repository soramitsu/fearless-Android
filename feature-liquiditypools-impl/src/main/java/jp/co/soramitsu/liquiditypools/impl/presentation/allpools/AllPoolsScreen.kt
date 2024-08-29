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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.common.compose.component.BackgroundCorneredWithBorder
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.models.TextModel
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white08
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_liquiditypools_impl.R
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.wallet.impl.presentation.balance.list.PullRefreshBox

data class AllPoolsState(
    val userPools: List<BasicPoolListItemState> = listOf(),
    val allPools: List<BasicPoolListItemState> = listOf(),
    val hasExtraUserPools: Boolean = false,
    val hasExtraAllPools: Boolean = false,
    val isLoading: Boolean = true
)

interface AllPoolsScreenInterface {
    fun onPoolClicked(pair: StringPair)
    fun onMoreClick(isUserPools: Boolean)
    fun onRefresh()
    fun onWindowHeightChange(heightIs: Dp)
    fun onHeaderHeightChange(heightIs: Dp)
}

@Composable
fun AllPoolsScreenWithRefresh(state: AllPoolsState, callback: AllPoolsScreenInterface) {
    PullRefreshBox(
        onRefresh = callback::onRefresh
    ) {
        AllPoolsScreen(state = state, callback = callback)
    }
}

@Composable
fun AllPoolsScreen(state: AllPoolsState, callback: AllPoolsScreenInterface) {
    val localDensity = LocalDensity.current
    var heightIs by remember {
        mutableStateOf(0.dp)
    }

    Box(
        modifier = Modifier
            .onGloballyPositioned { coordinates ->
                heightIs = with(localDensity) { coordinates.size.height.toDp() }
                callback.onWindowHeightChange(heightIs)
            }
            .fillMaxSize()
            .nestedScroll(rememberNestedScrollInteropConnection()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            MarginVertical(margin = 8.dp)

            if (state.isLoading || state.userPools.isEmpty() && state.allPools.isEmpty()) {
                BackgroundCorneredWithBorder(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                ) {
                    ShimmerPoolList()
                }
            } else {
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
                                title = stringResource(id = R.string.lp_user_pools_title),
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
                                modifier = Modifier.onGloballyPositioned { coordinates ->
                                    heightIs = with(localDensity) { coordinates.size.height.toDp() }
                                    callback.onHeaderHeightChange(heightIs)
                                },
                                title = stringResource(id = R.string.lp_available_pools_title),
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
                    MarginVertical(margin = 16.dp)
                }
            }
        }
    }
}

@Composable
fun ShimmerPoolList(size: Int = 10) {
    Column(
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier.wrapContentHeight()
    ) {
        PoolGroupHeader(
            title = stringResource(id = R.string.lp_available_pools_title),
            onMoreClick = null
        )
        repeat(size) {
            BasicPoolShimmerItem(modifier = Modifier.padding(vertical = Dimens.x1))
        }
    }
}

@Composable
private fun PoolGroupHeader(
    modifier: Modifier = Modifier,
    title: String,
    onMoreClick: (() -> Unit)?
) {
    Box(modifier = modifier.wrapContentHeight()) {
        Row(
            modifier = Modifier
                .padding(Dimens.x1_5)
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
                        .clickable(onClick = onMoreClick)
                        .padding(horizontal = Dimens.x1, vertical = 5.5.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.common_more).uppercase(),
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
private fun PreviewAllPoolsScreen() {
    val itemState = BasicPoolListItemState(
        ids = "0" to "1",
        token1Icon = "DEFAULT_ICON_URI",
        token2Icon = "DEFAULT_ICON_URI",
        text1 = "XOR-VAL",
        text2 = "123.4M",
        apy = LoadingState.Loaded("1234.3%"),
        text4 = TextModel.SimpleString("Earn SWAP"),
    )

    val items = listOf(
        itemState,
        itemState.copy(text1 = "TEXT1", text2 = "TEXT2", apy = LoadingState.Loaded("TEXT3"), text4 = TextModel.SimpleString("TEXT4")),
        itemState.copy(text1 = "text1", text2 = "text2", apy = LoadingState.Loaded("text3"), text4 = TextModel.SimpleString("text4")),
    )
    AllPoolsScreen(
        state = AllPoolsState(
            userPools = items,
            allPools = items,
//            isLoading = true
            isLoading = false,
            hasExtraUserPools = true
        ),
        callback = object : AllPoolsScreenInterface {
            override fun onPoolClicked(pair: StringPair) {}
            override fun onMoreClick(isUserPools: Boolean) {}
            override fun onRefresh() {}
            override fun onWindowHeightChange(heightIs: Dp) {}
            override fun onHeaderHeightChange(heightIs: Dp) {}
        },
    )
}
