package jp.co.soramitsu.wallet.impl.presentation.balance.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import jp.co.soramitsu.common.compose.component.ActionBar
import jp.co.soramitsu.common.compose.component.ActionBarViewState
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.AssetBalance
import jp.co.soramitsu.common.compose.component.AssetBalanceViewState
import jp.co.soramitsu.common.compose.component.B1
import jp.co.soramitsu.common.compose.component.B2
import jp.co.soramitsu.common.compose.component.BackgroundCornered
import jp.co.soramitsu.common.compose.component.CapsTitle2
import jp.co.soramitsu.common.compose.component.H5
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.Shimmer
import jp.co.soramitsu.common.compose.component.ShimmerRectangle
import jp.co.soramitsu.common.compose.component.getImageRequest
import jp.co.soramitsu.common.compose.theme.black3
import jp.co.soramitsu.common.compose.theme.black4
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.gray2
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.formatDateTime
import jp.co.soramitsu.common.utils.formatDaysSinceEpoch
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.SelectChainContent
import jp.co.soramitsu.wallet.impl.presentation.model.OperationModel
import jp.co.soramitsu.wallet.impl.presentation.model.OperationStatusAppearance
import jp.co.soramitsu.wallet.impl.presentation.transaction.history.mixin.TransactionHistoryUi
import jp.co.soramitsu.wallet.impl.presentation.transaction.history.model.DayHeader
import kotlinx.coroutines.launch

data class BalanceDetailsState(
    val balance: AssetBalanceViewState,
    val selectedChainId: String,
    val chainAssetId: String,
    val transactionHistory: TransactionHistoryUi.State
)

@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeUiApi::class)
@Composable
fun BalanceDetailsScreen(
    viewModel: BalanceDetailViewModel = hiltViewModel(),
    modalBottomSheetState: ModalBottomSheetState = rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)
) {
    val state by viewModel.state.collectAsState()
    val chainsState by viewModel.chainsState.collectAsState()

    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    ModalBottomSheetLayout(
        sheetShape = RoundedCornerShape(topEnd = 24.dp, topStart = 24.dp),
        sheetBackgroundColor = black4,
        sheetState = modalBottomSheetState,
        sheetContent = {
            SelectChainContent(
                state = chainsState,
                onChainSelected = { item ->
                    scope.launch {
                        viewModel.onChainSelected(item)
                        modalBottomSheetState.hide()
                    }
                    keyboardController?.hide()
                },
                onInput = viewModel::onChainSearchEntered
            )
        },
        content = {
            when (state) {
                is LoadingState.Loading<BalanceDetailsState> -> {
                    ShimmerBalanceDetailScreen()
                }
                is LoadingState.Loaded<BalanceDetailsState> -> {
                    val data = (state as LoadingState.Loaded<BalanceDetailsState>).data
                    ContentBalanceDetailsScreen(viewModel, data)
                }
                else -> {}
            }
        },
        scrimColor = Color.Black.copy(alpha = 0.32f) // https://issuetracker.google.com/issues/183697056
    )
}

@Composable
private fun ShimmerBalanceDetailScreen() {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MarginVertical(margin = 16.dp)
        Shimmer(
            Modifier
                .height(12.dp)
                .padding(horizontal = 120.dp)
        )
        MarginVertical(margin = 10.dp)
        Shimmer(
            Modifier
                .height(26.dp)
                .padding(horizontal = 93.dp)
        )
        MarginVertical(margin = 21.dp)
        Shimmer(
            Modifier
                .height(12.dp)
                .padding(horizontal = 133.dp)
        )
        MarginVertical(margin = 24.dp)
        Row(
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(4) {
                ShimmerRectangle(
                    Modifier.size(64.dp)
                )
                MarginHorizontal(margin = 16.dp)
            }
        }
        MarginVertical(margin = 16.dp)
        BackgroundCornered {
            Column(
                modifier = Modifier.padding(all = 12.dp)
            ) {
                Row(
                    verticalAlignment = CenterVertically
                ) {
                    Shimmer(
                        Modifier
                            .height(12.dp)
                            .width(124.dp)
                    )
                    Spacer(
                        modifier = Modifier
                            .height(1.dp)
                            .weight(1.0f)
                    )
                    Image(res = R.drawable.ic_filter_list_24)
                }
                MarginVertical(margin = 12.dp)
                Divider(
                    color = black3,
                    modifier = Modifier
                        .height(1.dp)
                        .fillMaxWidth()
                )
                MarginVertical(margin = 12.dp)
                ShimmerTransactionHistory()
            }
        }
    }
}

@Composable
private fun ContentBalanceDetailsScreen(
    viewModel: BalanceDetailViewModel,
    data: BalanceDetailsState
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MarginVertical(margin = 16.dp)
        AssetBalance(
            state = data.balance,
            onAddressClick = { },
            onBalanceClick = viewModel::frozenInfoClicked
        )
        MarginVertical(margin = 24.dp)
        ActionBar(
            state = ActionBarViewState(
                actionItems = mutableListOf(
                    ActionItemType.SEND,
                    ActionItemType.RECEIVE,
                    ActionItemType.TELEPORT
                ).apply {
                    if (viewModel.buyEnabled()) add(ActionItemType.BUY)
                },
                chainId = data.selectedChainId,
                chainAssetId = data.chainAssetId
            ),
            onItemClick = viewModel::actionItemClicked
        )
        MarginVertical(margin = 16.dp)
        BackgroundCornered {
            Column(
                modifier = Modifier.padding(all = 12.dp)
            ) {
                Row(
                    verticalAlignment = CenterVertically
                ) {
                    H5(
                        text = stringResource(id = R.string.common_all_transactions),
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .weight(1f)
                    )
                    Image(
                        res = R.drawable.ic_filter_list_24,
                        modifier = Modifier.clickable {
                            viewModel.filterClicked()
                        }
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
                    viewModel = viewModel,
                    history = data.transactionHistory
                )
            }
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
                    verticalAlignment = CenterVertically
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
private fun TransactionHistory(
    viewModel: BalanceDetailViewModel,
    history: TransactionHistoryUi.State
) {
    when (history) {
        is TransactionHistoryUi.State.Data -> {
            val isRefreshing by viewModel.isRefreshing.collectAsState()
            val listState = rememberLazyListState()
            val transactions = history.items

            SwipeRefresh(
                state = rememberSwipeRefreshState(isRefreshing),
                onRefresh = viewModel::sync
            ) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(15.dp),
                    modifier = Modifier
                        .scrollable(
                            orientation = Orientation.Vertical,
                            state = rememberScrollableState { delta ->
                                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let {
                                    viewModel.transactionsScrolled(it)
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
                                    viewModel = viewModel,
                                    item = item
                                )
                            }
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
                horizontalAlignment = CenterHorizontally
            ) {
                MarginVertical(margin = 12.dp)
                H5(
                    text = stringResource(id = R.string.transfers_empty),
                    color = gray2
                )
                MarginVertical(margin = 120.dp)
            }
        }
    }
}

@Composable
private fun TransactionItem(
    viewModel: BalanceDetailViewModel,
    item: OperationModel
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable {
            viewModel.transactionClicked(item)
        }
    ) {
        AsyncImage(
            model = getImageRequest(LocalContext.current, item.assetIconUrl.orEmpty()),
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .align(Alignment.CenterVertically)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.Start) {
            B1(
                text = item.header,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(100.dp)
            )
            B2(
                text = item.subHeader,
                textAlign = TextAlign.Start,
                maxLines = 1,
                color = Color.White.copy(alpha = 0.64f)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Row {
                val balanceChangeStatusColor = if (item.amount.startsWith("+")) {
                    MaterialTheme.customColors.greenText
                } else {
                    MaterialTheme.customColors.white
                }

                B1(
                    text = item.amount,
                    color = balanceChangeStatusColor,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(5.dp))
                if (item.statusAppearance != OperationStatusAppearance.COMPLETED) {
                    Image(
                        res = item.statusAppearance.icon,
                        modifier = Modifier.align(CenterVertically)
                    )
                }
            }
            B2(
                text = item.time.formatDateTime(LocalContext.current).toString(),
                textAlign = TextAlign.End,
                maxLines = 1,
                color = Color.White.copy(alpha = 0.64f)
            )
        }
    }
}
