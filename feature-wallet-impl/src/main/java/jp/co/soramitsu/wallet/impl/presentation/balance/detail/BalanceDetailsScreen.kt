package jp.co.soramitsu.wallet.impl.presentation.balance.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import jp.co.soramitsu.common.compose.component.ActionBar
import jp.co.soramitsu.common.compose.component.ActionBarShimmer
import jp.co.soramitsu.common.compose.component.ActionBarViewState
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.AssetBalance
import jp.co.soramitsu.common.compose.component.AssetBalanceShimmer
import jp.co.soramitsu.common.compose.component.AssetBalanceViewState
import jp.co.soramitsu.common.compose.component.BackgroundCornered
import jp.co.soramitsu.common.compose.component.CapsTitle2
import jp.co.soramitsu.common.compose.component.ChangeBalanceViewState
import jp.co.soramitsu.common.compose.component.H5
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.InfoTableItem
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.Shimmer
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black
import jp.co.soramitsu.common.compose.theme.black3
import jp.co.soramitsu.common.compose.theme.gray2
import jp.co.soramitsu.common.compose.theme.white08
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.formatDaysSinceEpoch
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.presentation.balance.detail.frozen.ExpandableLazyListNestedScrollConnection
import jp.co.soramitsu.wallet.impl.presentation.model.OperationModel
import jp.co.soramitsu.wallet.impl.presentation.transaction.history.mixin.TransactionHistoryUi
import jp.co.soramitsu.wallet.impl.presentation.transaction.history.model.DayHeader

data class BalanceDetailsState(
    val actionBarViewState: LoadingState<ActionBarViewState>,
    val balance: LoadingState<AssetBalanceViewState>,
    val transferableViewState: TitleValueViewState,
    val lockedViewState: TitleValueViewState,
    val transactionHistory: TransactionHistoryUi.State
)

interface BalanceDetailsScreenInterface {
    fun onAddressClick()
    fun actionItemClicked(actionType: ActionItemType, chainId: ChainId, chainAssetId: String)
    fun filterClicked()
    fun transactionClicked(transactionModel: OperationModel)
    fun sync()
    fun transactionsScrolled(index: Int)
    fun tableItemClicked(itemId: Int)
}

@Composable
fun BalanceDetailsScreen(
    state: BalanceDetailsState,
    callback: BalanceDetailsScreenInterface
) {
    val parentHeight = remember { mutableStateOf(0.dp) }
    val blockHeight = remember { mutableStateOf(0.dp) }
    val ld = LocalDensity.current
    val height = remember { mutableStateOf(0.dp) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val nestedScrollConnection = remember {
        ExpandableLazyListNestedScrollConnection(
            listState,
            parentHeight,
            blockHeight,
            height,
            scope,
            ld.density
        ) {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let {
                callback.transactionsScrolled(it)
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) parent@{
        parentHeight.value = this@parent.maxHeight

        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxSize()
            ) {
                MarginVertical(margin = 16.dp)
                AssetBalance(
                    balanceLoadingState = state.balance,
                    onAddressClick = callback::onAddressClick
                )
                MarginVertical(margin = 24.dp)
                BackgroundCornered {
                    Column {
                        InfoTableItem(state = state.transferableViewState)
                        InfoTableItem(state = state.lockedViewState, onClick = callback::tableItemClicked)
                        Divider(
                            color = white08,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                        ActionBar(
                            actionBarLoadingState = state.actionBarViewState,
                            actionItemClicked = callback::actionItemClicked
                        )
                    }
                }
                MarginVertical(margin = 16.dp)
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxHeight()
                ) asd@{
                    blockHeight.value = this@asd.maxHeight
                    LaunchedEffect(key1 = blockHeight.value, block = {
                        height.value = this@asd.maxHeight
                    })
                }
            }
        }

        val dragState = rememberDraggableState(onDelta = {
            nestedScrollConnection.preScrollFromParentLayout(Offset(0f, it))
        })
        val padding = ((1 - nestedScrollConnection.expandPercent) * 16).dp
        Column(
            modifier = Modifier
                .height(height.value)
                .align(Alignment.BottomCenter)
                .padding(horizontal = padding)
                .draggable(dragState, Orientation.Vertical, onDragStopped = {
                    nestedScrollConnection.onPostFling(Velocity.Zero, Velocity(0f, it))
                })
        ) {
            val alpha = if (nestedScrollConnection.expandPercent.isNaN() || nestedScrollConnection.expandPercent < 0f) {
                0f
            } else {
                nestedScrollConnection.expandPercent
            }
            val background = black.copy(alpha = alpha)
            BackgroundCornered(modifier = Modifier.height(height.value)) {
                Column(
                    modifier = Modifier
                        .background(background)
                        .padding(top = 12.dp, start = 12.dp, end = 12.dp)
                        .fillMaxSize()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        H5(
                            text = stringResource(id = R.string.common_all_transactions),
                            textAlign = TextAlign.Start,
                            modifier = Modifier
                                .weight(1f)
                        )
                        Image(
                            res = R.drawable.ic_filter_list_24,
                            modifier = Modifier.clickable(onClick = callback::filterClicked)
                        )
                    }
                    MarginVertical(margin = 12.dp)
                    Divider(
                        color = black3,
                        modifier = Modifier
                            .height(1.dp)
                            .fillMaxWidth()
                    )
                    MarginVertical(margin = 12.dp)
                    TransactionHistory(
                        nestedScrollConnection = nestedScrollConnection,
                        listState = listState,
                        history = state.transactionHistory,
                        transactionClicked = callback::transactionClicked,
                        height = height,
                        onRefresh = callback::sync
                    )
                }
            }
        }
    }
}

@Composable
private fun AssetBalance(
    balanceLoadingState: LoadingState<AssetBalanceViewState>,
    onAddressClick: () -> Unit
) {
    when (balanceLoadingState) {
        is LoadingState.Loading -> {
            AssetBalanceShimmer()
        }
        is LoadingState.Loaded -> {
            AssetBalance(state = balanceLoadingState.data, onAddressClick = onAddressClick)
        }
    }
}

@Composable
private fun ActionBar(actionBarLoadingState: LoadingState<ActionBarViewState>, actionItemClicked: (ActionItemType, String, String) -> Unit) {
    when (actionBarLoadingState) {
        is LoadingState.Loading -> {
            ActionBarShimmer(fillMaxWidth = true)
        }
        is LoadingState.Loaded -> {
            ActionBar(
                state = actionBarLoadingState.data,
                fillMaxWidth = true,
                onItemClick = actionItemClicked
            )
        }
    }
}

@Composable
private fun TransactionHistory(
    nestedScrollConnection: ExpandableLazyListNestedScrollConnection,
    history: TransactionHistoryUi.State,
    height: MutableState<Dp>,
    transactionClicked: (OperationModel) -> Unit,
    onRefresh: () -> Unit,
    listState: LazyListState
) {
    SwipeRefresh(
        state = rememberSwipeRefreshState(history is TransactionHistoryUi.State.Refreshing),
        swipeEnabled = false,
        onRefresh = onRefresh
    ) {
        when (history) {
            is TransactionHistoryUi.State.Data -> {
                val transactions = history.items
                LazyColumn(
                    modifier = Modifier
                        .height(height.value)
                        .nestedScroll(nestedScrollConnection),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(15.dp)
                ) {
                    items(transactions) { item ->
                        when (item) {
                            is DayHeader -> {
                                CapsTitle2(
                                    text = item.daysSinceEpoch
                                        .formatDaysSinceEpoch(LocalContext.current)
                                        .orEmpty()
                                        .uppercase(),
                                    textAlign = TextAlign.Start
                                )
                            }
                            is OperationModel -> {
                                TransactionItem(
                                    item = item,
                                    transactionClicked = transactionClicked
                                )
                            }
                        }
                    }
                }
            }
            is TransactionHistoryUi.State.EmptyProgress -> {
                ShimmerTransactionHistory()
            }
            is TransactionHistoryUi.State.Empty -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MarginVertical(margin = 12.dp)
                    H5(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(id = R.string.transfers_empty),
                        color = gray2,
                        textAlign = TextAlign.Center
                    )
                    MarginVertical(margin = 120.dp)
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun ShimmerTransactionHistory() {
    repeat(2) {
        Column {
            Shimmer(
                Modifier
                    .height(10.dp)
                    .width(43.dp)
            )
            MarginVertical(margin = 8.dp)
            repeat(2) {
                Row(
                    Modifier
                        .height(IntrinsicSize.Min)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Shimmer(Modifier.size(30.dp))
                    MarginHorizontal(margin = 8.dp)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(15.dp)
                    ) {
                        Shimmer(
                            Modifier
                                .height(10.dp)
                                .width(60.dp)
                        )
                        Shimmer(
                            Modifier
                                .height(10.dp)
                                .width(43.dp)
                        )
                    }
                    Spacer(
                        modifier = Modifier
                            .height(1.dp)
                            .weight(1.0f)
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(15.dp)
                    ) {
                        Shimmer(
                            Modifier
                                .height(10.dp)
                                .width(60.dp)
                        )
                        Shimmer(
                            Modifier
                                .height(10.dp)
                                .width(43.dp)
                        )
                    }
                }
                MarginVertical(margin = 15.dp)
            }
        }
    }
}

@Composable
@Preview
private fun PreviewBalanceDetailScreenContent() {
    val percentChange = "+5.67%"
    val assetBalance = "44400.3"
    val assetBalanceFiat = "$2345.32"
    val address = "0x32141235qwegtf24315reqwerfasdgqwert243rfasdvgergsdf"

    val assetBalanceViewState = AssetBalanceViewState(
        balance = assetBalance,
        address = address,
        isInfoEnabled = false,
        changeViewState = ChangeBalanceViewState(
            percentChange = percentChange,
            fiatChange = assetBalanceFiat
        )
    )

    val state = BalanceDetailsState(
        actionBarViewState = LoadingState.Loaded(
            ActionBarViewState(
                chainAssetId = "0x123",
                chainId = "0x123",
                actionItems = listOf(ActionItemType.SEND, ActionItemType.RECEIVE, ActionItemType.SWAP)
            )
        ),
        balance = LoadingState.Loaded(assetBalanceViewState),
        transactionHistory = TransactionHistoryUi.State.Empty(),
        transferableViewState = TitleValueViewState(title = stringResource(R.string.assetdetails_balance_transferable)),
        lockedViewState = TitleValueViewState(
            title = stringResource(R.string.assetdetails_balance_locked),
            clickState = TitleValueViewState.ClickState.Title(R.drawable.ic_info_14, 1)
        )
    )

    val empty = object : BalanceDetailsScreenInterface {
        override fun onAddressClick() {}

        override fun actionItemClicked(actionType: ActionItemType, chainId: ChainId, chainAssetId: String) {}
        override fun filterClicked() {}
        override fun transactionClicked(transactionModel: OperationModel) {}
        override fun sync() {}
        override fun transactionsScrolled(index: Int) {}
        override fun tableItemClicked(itemId: Int) = Unit
    }

    return FearlessTheme {
        Box(modifier = Modifier.height(600.dp)) {
            BalanceDetailsScreen(state = state, callback = empty)
        }
    }
}
