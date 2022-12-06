package jp.co.soramitsu.wallet.impl.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.HiddenAssetsItem
import jp.co.soramitsu.common.compose.component.HiddenItemState
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.SwipeState
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.launch

interface AssetsListInterface {
    @OptIn(ExperimentalMaterialApi::class)
    fun actionItemClicked(actionType: ActionItemType, chainId: ChainId, chainAssetId: String, swipeableState: SwipeableState<SwipeState>)
    fun assetClicked(state: AssetListItemViewState)
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AssetsList(
    data: AssetListState,
    callback: AssetsListInterface
) {
    val listState = rememberLazyListState()
    val isShowHidden = remember { mutableStateOf(false) }
    val onHiddenClick = remember { { isShowHidden.value = isShowHidden.value.not() } }

    LaunchedEffect(
        key1 = data.assets.size,
        block = {
            launch {
                listState.scrollToItem(0)
            }
        }
    )
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(data.visibleAssets, key = { it.displayName }) { assetState ->
            SwipableAssetListItem(
                assetState = assetState,
                assetClicked = callback::assetClicked,
                actionItemClicked = callback::actionItemClicked
            )
        }
        if (data.hiddenAssets.isNotEmpty()) {
            item {
                HiddenAssetsItem(
                    state = HiddenItemState(isShowHidden.value),
                    onClick = onHiddenClick
                )
            }
            if (isShowHidden.value) {
                items(data.hiddenAssets, key = { it.displayName }) { assetState ->
                    SwipableAssetListItem(
                        assetState = assetState,
                        assetClicked = callback::assetClicked,
                        actionItemClicked = callback::actionItemClicked
                    )
                }
            }
        }
        item { MarginVertical(margin = 80.dp) }
    }
}
