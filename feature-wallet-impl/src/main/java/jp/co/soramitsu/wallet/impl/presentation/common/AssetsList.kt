package jp.co.soramitsu.wallet.impl.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.SwipeState
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

interface AssetsListInterface {
    @OptIn(ExperimentalMaterialApi::class)
    fun actionItemClicked(actionType: ActionItemType, chainId: ChainId, chainAssetId: String, swipeableState: SwipeableState<SwipeState>)
    fun assetClicked(state: AssetListItemViewState)
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AssetsList(
    data: AssetListState,
    callback: AssetsListInterface,
    listState: LazyListState = rememberLazyListState(),
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null
) {
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 8.dp)
    ) {
        if (header != null) {
            item { header() }
        }
        if (data.assets.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    B0(
                        text = stringResource(id = R.string.wallet_all_assets_hidden),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(data.assets, key = { it.key }) { assetState ->
                SwipeableAssetListItem(
                    assetState = assetState,
                    assetClicked = callback::assetClicked,
                    actionItemClicked = callback::actionItemClicked
                )
            }
        }
        if (footer != null) {
            item { footer() }
        }
        item { MarginVertical(margin = 80.dp) }
    }
}
