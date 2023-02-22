package jp.co.soramitsu.wallet.impl.presentation.common

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.compose.material.rememberSwipeableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.ActionBar
import jp.co.soramitsu.common.compose.component.ActionBarViewState
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.AssetListItem
import jp.co.soramitsu.common.compose.component.SwipeBox
import jp.co.soramitsu.common.compose.component.SwipeBoxViewState
import jp.co.soramitsu.common.compose.component.SwipeState
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SwipeableAssetListItem(
    assetState: AssetListItemViewState,
    assetClicked: (AssetListItemViewState) -> Unit,
    actionItemClicked: (actionType: ActionItemType, chainId: ChainId, chainAssetId: String, swipeableState: SwipeableState<SwipeState>) -> Unit
) {
    val swipeableState = rememberSwipeableState(initialValue = SwipeState.INITIAL)

    fun onItemClick(actionType: ActionItemType, chainId: ChainId, chainAssetId: String) {
        actionItemClicked(actionType, chainId, chainAssetId, swipeableState)
    }

    val swipeBoxViewState = remember {
        SwipeBoxViewState(
            leftStateWidth = 170.dp,
            rightStateWidth = 90.dp
        )
    }

    val leftBarActionViewState = remember { getLeftActionBarViewState(assetState) }
    val rightBarActionViewState = remember { getRightActionBarViewState(assetState) }
    SwipeBox(
        swipeableState = swipeableState,
        state = swipeBoxViewState,
        initialContent = {
            AssetListItem(
                state = assetState,
                onClick = assetClicked
            )
        },
        leftContent = {
            ActionBar(
                state = leftBarActionViewState,
                onItemClick = ::onItemClick
            )
        },
        rightContent = {
            ActionBar(
                state = rightBarActionViewState,
                onItemClick = ::onItemClick
            )
        }
    )
}

private fun getLeftActionBarViewState(asset: AssetListItemViewState) = ActionBarViewState(
    chainId = asset.chainId,
    chainAssetId = asset.chainAssetId,
    actionItems = listOf(
        ActionItemType.SEND,
        ActionItemType.RECEIVE
    )
)

private fun getRightActionBarViewState(asset: AssetListItemViewState) = ActionBarViewState(
    chainId = asset.chainId,
    chainAssetId = asset.chainAssetId,
    actionItems = listOf(
        when {
            asset.isHidden -> ActionItemType.SHOW
            else -> ActionItemType.HIDE
        }
    )
)
