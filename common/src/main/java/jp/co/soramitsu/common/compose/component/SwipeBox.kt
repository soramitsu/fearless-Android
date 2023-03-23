package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.SwipeableState
import androidx.compose.material.rememberSwipeableState
import androidx.compose.material.swipeable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

enum class SwipeState {
    LEFT,
    RIGHT,
    INITIAL
}

data class SwipeBoxViewState(
    val leftStateWidth: Dp,
    val rightStateWidth: Dp
)

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SwipeBox(
    state: SwipeBoxViewState,
    swipeableState: SwipeableState<SwipeState>,
    initialContent: @Composable () -> Unit,
    leftContent: @Composable () -> Unit,
    rightContent: @Composable () -> Unit
) {
    val currentDensity = LocalDensity.current
    val anchors = remember {
        mapOf(
            with(currentDensity) { state.leftStateWidth.toPx() } to SwipeState.LEFT,
            with(currentDensity) { -state.rightStateWidth.toPx() } to SwipeState.RIGHT,
            0f to SwipeState.INITIAL
        )
    }
    val thresholds = remember { { _: SwipeState, _: SwipeState -> FractionalThreshold(0.3f) } }
    val leftOffsetsCalculator = remember<Density.() -> IntOffset> {
        {
            val offsetX = -state.leftStateWidth.toPx() + swipeableState.offset.value

            val extraOffset = -10.dp.toPx() // hiding beyond screen
            val eliminationDistance = 40.dp.toPx()
            val eliminationCoefficient = when {
                swipeableState.offset.value > eliminationDistance -> 0f
                else -> (eliminationDistance - swipeableState.offset.value) / eliminationDistance
            }

            val x = (offsetX + extraOffset * eliminationCoefficient).roundToInt()
            IntOffset(x, 0)
        }
    }
    val rightOffsetsCalculator = remember<Density.() -> IntOffset> {
        {
            val offsetX = (state.rightStateWidth.toPx() + swipeableState.offset.value).roundToInt()

            val extraOffset = 10.dp.toPx() // hiding beyond screen
            val eliminationDistance = 40.dp.toPx()
            val eliminationCoefficient = when {
                swipeableState.offset.value.absoluteValue > eliminationDistance -> 0f
                else -> (eliminationDistance - swipeableState.offset.value.absoluteValue) / eliminationDistance
            }

            val x = (offsetX + extraOffset * eliminationCoefficient).roundToInt()
            IntOffset(x, 0)
        }
    }
    val contentOffset = remember<Density.() -> IntOffset> { { IntOffset(swipeableState.offset.value.roundToInt(), 0) } }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .swipeable(
                state = swipeableState,
                anchors = anchors,
                thresholds = thresholds,
                orientation = Orientation.Horizontal
            )
            .testTag("SwipeBox")
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .wrapContentHeight()
                .offset(leftOffsetsCalculator)
        ) {
            leftContent()
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .wrapContentHeight()
                .offset(rightOffsetsCalculator)
        ) {
            rightContent()
        }

        Box(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .offset(contentOffset)
        ) {
            initialContent()
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Preview
@Composable
private fun AssetItemSwipeBoxPreview() {
    val leftActionBarViewState = ActionBarViewState(
        chainId = "",
        chainAssetId = "",
        actionItems = listOf(
            ActionItemType.SEND,
            ActionItemType.RECEIVE,
            ActionItemType.TELEPORT
        )
    )

    val rightActionBarViewState = ActionBarViewState(
        chainId = "",
        chainAssetId = "",
        actionItems = listOf(
            ActionItemType.HIDE
        )
    )

    val assetIconUrl = "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Polkadot.svg"
    val assetName = "Karura asset"
    val assetChainName = "Karura"
    val assetSymbol = "KSM"
    val assetTokenFiat = "$73.22"
    val assetTokenRate = "+5.67%"
    val assetTransferableBalance = "444.3"
    val assetTransferableBalanceFiat = "$2345.32"
    val assetChainUrlsMap = mapOf(
        "" to "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Karura.svg",
        "" to "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/kilt.svg",
        "" to "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Moonriver.svg",
        "" to "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Polkadot.svg",
        "" to "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Moonbeam.svg",
        "" to "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Statemine.svg",
        "" to "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Rococo.svg"
    )

    val assetListItemViewState = AssetListItemViewState(
        assetIconUrl = assetIconUrl,
        assetChainName = assetChainName,
        assetName = assetName,
        assetSymbol = assetSymbol,
        assetTokenFiat = assetTokenFiat,
        assetTokenRate = assetTokenRate,
        assetTransferableBalance = assetTransferableBalance,
        assetTransferableBalanceFiat = assetTransferableBalanceFiat,
        assetChainUrls = assetChainUrlsMap,
        chainId = "",
        chainAssetId = "",
        isSupported = true,
        isHidden = false,
        displayName = assetSymbol,
        hasAccount = true,
        priceId = null,
        hasNetworkIssue = false
    )

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        SwipeBox(
            state = SwipeBoxViewState(
                leftStateWidth = 250.dp,
                rightStateWidth = 90.dp
            ),
            swipeableState = rememberSwipeableState(SwipeState.INITIAL),
            leftContent = { ActionBar(leftActionBarViewState) },
            rightContent = { ActionBar(rightActionBarViewState) },
            initialContent = { AssetListItem(state = assetListItemViewState, onClick = {}) }
        )
    }
}
