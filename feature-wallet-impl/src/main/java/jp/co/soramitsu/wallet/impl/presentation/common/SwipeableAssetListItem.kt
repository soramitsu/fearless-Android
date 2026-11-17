package jp.co.soramitsu.wallet.impl.presentation.common

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.compose.material.rememberSwipeableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.ActionBar
import jp.co.soramitsu.common.compose.component.ActionBarViewState
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.AssetListItem
import jp.co.soramitsu.common.compose.component.BackgroundCornered
import jp.co.soramitsu.common.compose.component.SwipeBox
import jp.co.soramitsu.common.compose.component.SwipeBoxViewState
import jp.co.soramitsu.common.compose.component.SwipeState
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.common.utils.onSingleClick
import jp.co.soramitsu.common.utils.rememberLastClickTime
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SwipeableAssetListItem(
    assetState: AssetListItemViewState,
    isHideVisible: Boolean,
    assetClicked: (AssetListItemViewState) -> Unit,
    actionItemClicked: (actionType: ActionItemType, chainId: ChainId, chainAssetId: String, swipeableState: SwipeableState<SwipeState>) -> Unit,
    /** The visibility action behind the row; by default Show for a hidden row and Hide otherwise. */
    visibilityAction: ActionItemType = if (assetState.isHidden) ActionItemType.SHOW else ActionItemType.HIDE,
    /** False where the list around the row already names the network. */
    showNetwork: Boolean = true
) {
    val swipeableState = rememberSwipeableState(initialValue = SwipeState.INITIAL)

    fun onItemClick(actionType: ActionItemType, chainId: ChainId, chainAssetId: String) {
        actionItemClicked(actionType, chainId, chainAssetId, swipeableState)
    }

    val swipeBoxViewState = remember {
        SwipeBoxViewState(
            leftStateWidth = 170.dp,
            rightStateWidth = if (isHideVisible) 90.dp else 0.dp
        )
    }

    val leftBarActionViewState = remember { getLeftActionBarViewState(assetState) }
    val rightBarActionViewState = remember { getRightActionBarViewState(assetState, visibilityAction) }
    // A swipe is a gesture a screen reader cannot make: the actions behind the row are also the row's own.
    val swipeActions = leftBarActionViewState.actionItems + if (isHideVisible) rightBarActionViewState.actionItems else emptyList()
    val swipeActionLabels = swipeActions.map { stringResource(it.titleId) }
    // The same double-tap guard as the visible action buttons, so a repeated screen-reader action opens one screen.
    val lastActionTime = rememberLastClickTime()
    SwipeBox(
        swipeableState = swipeableState,
        state = swipeBoxViewState,
        initialContent = {
            AssetListItem(
                state = assetState,
                modifier = Modifier.semantics {
                    customActions = swipeActions.mapIndexed { index, action ->
                        CustomAccessibilityAction(swipeActionLabels[index]) {
                            onSingleClick(lastActionTime) { onItemClick(action, assetState.chainId, assetState.chainAssetId) }
                            true
                        }
                    }
                },
                showNetwork = showNetwork,
                onClick = assetClicked
            )
        },
        leftContent = {
            BackgroundCornered {
                ActionBar(
                    state = leftBarActionViewState,
                    onItemClick = ::onItemClick
                )
            }
        },
        rightContent = {
            if (isHideVisible) {
                BackgroundCornered {
                    ActionBar(
                        state = rightBarActionViewState,
                        onItemClick = ::onItemClick
                    )
                }
            }
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

private fun getRightActionBarViewState(asset: AssetListItemViewState, visibilityAction: ActionItemType) = ActionBarViewState(
    chainId = asset.chainId,
    chainAssetId = asset.chainAssetId,
    actionItems = listOf(visibilityAction)
)
