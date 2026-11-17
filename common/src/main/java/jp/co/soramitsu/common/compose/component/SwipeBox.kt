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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
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
            val eliminationDistance = REVEAL_DISTANCE.toPx()
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
            val eliminationDistance = REVEAL_DISTANCE.toPx()
            val eliminationCoefficient = when {
                swipeableState.offset.value.absoluteValue > eliminationDistance -> 0f
                else -> (eliminationDistance - swipeableState.offset.value.absoluteValue) / eliminationDistance
            }

            val x = (offsetX + extraOffset * eliminationCoefficient).roundToInt()
            IntOffset(x, 0)
        }
    }
    val contentOffset = remember<Density.() -> IntOffset> { { IntOffset(swipeableState.offset.value.roundToInt(), 0) } }
    // The actions exist only while the row is swiped their way. At rest nothing of them is drawn beside the
    // row (their cards showed in the page gutter) and no unseen button waits there for a tap.
    val leftRevealed by remember(swipeableState) { derivedStateOf { swipeableState.offset.value > 0f } }
    val rightRevealed by remember(swipeableState) { derivedStateOf { swipeableState.offset.value < 0f } }
    // They fade in over the first part of the swipe instead of appearing at once in a wide gutter.
    val reveal = remember<GraphicsLayerScope.() -> Unit>(swipeableState) {
        {
            alpha = (swipeableState.offset.value.absoluteValue / REVEAL_DISTANCE.toPx()).coerceIn(0f, 1f)
            // No offscreen buffer: it would cut the cards' shadows at the layer's bounds.
            compositingStrategy = CompositingStrategy.ModulateAlpha
        }
    }
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
        // The row in front sets the height: the actions behind it must never make the row taller.
        if (leftRevealed) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(leftOffsetsCalculator)
                    .graphicsLayer(reveal),
                contentAlignment = Alignment.CenterStart
            ) {
                leftContent()
            }
        }

        if (rightRevealed) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(rightOffsetsCalculator)
                    .graphicsLayer(reveal),
                contentAlignment = Alignment.CenterEnd
            ) {
                rightContent()
            }
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

/** The swipe distance over which the actions slide in from beyond the row and reach full strength. */
private val REVEAL_DISTANCE = 40.dp

@OptIn(ExperimentalMaterialApi::class)
@Preview
@Composable
private fun AssetItemSwipeBoxPreview() {
    val leftActionBarViewState = ActionBarViewState(
        chainId = "",
        chainAssetId = "",
        actionItems = listOf(
            ActionItemType.SEND,
            ActionItemType.RECEIVE
        )
    )

    val rightActionBarViewState = ActionBarViewState(
        chainId = "",
        chainAssetId = "",
        actionItems = listOf(
            ActionItemType.HIDE
        )
    )

    val assetChainUrlsMap = mapOf(
        "" to "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/assets/icons/chains/white/Karura.svg",
        "" to "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/assets/icons/chains/white/kilt.svg",
        "" to "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/assets/icons/chains/white/Moonriver.svg",
        "" to "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/assets/icons/chains/white/Polkadot.svg",
        "" to "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/assets/icons/chains/white/Moonbeam.svg",
        "" to "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/assets/icons/chains/white/Statemine.svg",
        "" to "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/assets/icons/chains/white/Rococo.svg"
    )

    val assetListItemViewState = AssetListItemViewState(
        index = 0,
        assetIconUrl = "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/assets/icons/chains/white/Polkadot.svg",
        assetChainName = "Karura",
        assetName = "Karura asset",
        assetSymbol = "KSM",
        assetTokenFiat = "$73.2200000003",
        assetTokenRate = "+5.67%",
        assetTransferableBalance = "444.3",
        assetTransferableBalanceFiat = "$2345.30000002",
        assetChainUrls = assetChainUrlsMap,
        chainId = "",
        chainAssetId = "",
        isSupported = true,
        isHidden = false,
        isTestnet = false
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
