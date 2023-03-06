package jp.co.soramitsu.wallet.impl.presentation.balance.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
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
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.Shimmer
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black3
import jp.co.soramitsu.common.compose.theme.gray2
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.formatDaysSinceEpoch
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.presentation.model.OperationModel
import jp.co.soramitsu.wallet.impl.presentation.transaction.history.mixin.TransactionHistoryUi
import jp.co.soramitsu.wallet.impl.presentation.transaction.history.model.DayHeader

data class BalanceDetailsState(
    val actionBarViewState: LoadingState<ActionBarViewState>,
    val balance: LoadingState<AssetBalanceViewState>,
    val transactionHistory: TransactionHistoryUi.State
)

interface BalanceDetailsScreenInterface {
    fun onAddressClick()
    fun onBalanceClick()
    fun actionItemClicked(actionType: ActionItemType, chainId: ChainId, chainAssetId: String)
    fun filterClicked()
    fun transactionClicked(transactionModel: OperationModel)
    fun sync()
    fun transactionsScrolled(index: Int)
}

@Composable
fun BalanceDetailsScreen(
    state: BalanceDetailsState,
    callback: BalanceDetailsScreenInterface
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MarginVertical(margin = 16.dp)
        AssetBalance(
            balanceLoadingState = state.balance,
            onAddressClick = callback::onAddressClick,
            onBalanceClick = callback::onBalanceClick
        )
        MarginVertical(margin = 24.dp)
        ActionBar(
            actionBarLoadingState = state.actionBarViewState,
            actionItemClicked = callback::actionItemClicked
        )
        MarginVertical(margin = 16.dp)
        BackgroundCornered {
            Column(
                modifier = Modifier
                    .padding(all = 12.dp)
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
                    history = state.transactionHistory,
                    transactionClicked = callback::transactionClicked,
                    onRefresh = callback::sync,
                    transactionsScrolled = callback::transactionsScrolled
                )
            }
        }
    }
}

@Composable
private fun AssetBalance(
    balanceLoadingState: LoadingState<AssetBalanceViewState>,
    onAddressClick: () -> Unit,
    onBalanceClick: () -> Unit
) {
    when (balanceLoadingState) {
        is LoadingState.Loading -> {
            AssetBalanceShimmer()
        }
        is LoadingState.Loaded -> {
            AssetBalance(state = balanceLoadingState.data, onAddressClick = onAddressClick, onBalanceClick = onBalanceClick)
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
    history: TransactionHistoryUi.State,
    transactionClicked: (OperationModel) -> Unit,
    onRefresh: () -> Unit,
    transactionsScrolled: (index: Int) -> Unit
) {
    SwipeRefresh(
        state = rememberSwipeRefreshState(history is TransactionHistoryUi.State.Refreshing),
        onRefresh = onRefresh
    ) {
        when (history) {
            is TransactionHistoryUi.State.Data -> {
                val listState = rememberLazyListState()
                val transactions = history.items
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(15.dp),
                    modifier = Modifier
                        .scrollable(
                            orientation = Orientation.Vertical,
                            state = rememberScrollableState { delta ->
                                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let {
                                    transactionsScrolled(it)
                                }
                                delta
                            }
                        )
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
        isInfoEnabled = true,
        changeViewState = ChangeBalanceViewState(
            percentChange = percentChange,
            fiatChange = assetBalanceFiat
        )
    )

    val state = BalanceDetailsState(
        actionBarViewState = LoadingState.Loaded(ActionBarViewState(chainAssetId = "0x123", chainId = "0x123", actionItems = listOf())),
        balance = LoadingState.Loaded(assetBalanceViewState),
        transactionHistory = TransactionHistoryUi.State.Empty()
    )

    val empty = object : BalanceDetailsScreenInterface {
        override fun onAddressClick() {}
        override fun onBalanceClick() {}

        override fun actionItemClicked(actionType: ActionItemType, chainId: ChainId, chainAssetId: String) {}
        override fun filterClicked() {}
        override fun transactionClicked(transactionModel: OperationModel) {}
        override fun sync() {}
        override fun transactionsScrolled(index: Int) {}
    }

    return FearlessTheme {
        Box(modifier = Modifier.height(600.dp)) {
            BalanceDetailsScreen(state = state, callback = empty)
        }
    }
}
