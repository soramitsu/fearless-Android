package jp.co.soramitsu.common.compose.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.rememberSwipeableState
import androidx.compose.material.swipeable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
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
    initialContent: @Composable () -> Unit,
    leftContent: @Composable () -> Unit,
    rightContent: @Composable () -> Unit
) {
    val swipeableState = rememberSwipeableState(SwipeState.INITIAL)
    val anchors = mapOf(
        with(LocalDensity.current) { state.leftStateWidth.toPx() } to SwipeState.LEFT,
        with(LocalDensity.current) { -state.rightStateWidth.toPx() } to SwipeState.RIGHT,
        0f to SwipeState.INITIAL
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .swipeable(
                state = swipeableState,
                anchors = anchors,
                thresholds = { _, _ -> FractionalThreshold(0.3f) },
                orientation = Orientation.Horizontal
            )
            .testTag("SwipeBox")
    ) {
        AnimatedVisibility(
            visible = swipeableState.currentValue == SwipeState.LEFT,
            modifier = Modifier.align(Alignment.CenterStart),
            enter = fadeIn() + slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth }
            ),
            exit = fadeOut() + slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth }
            )
        ) {
            Box(
                modifier = Modifier
                    .wrapContentHeight()
            ) {
                leftContent()
            }
        }

        AnimatedVisibility(
            visible = swipeableState.currentValue == SwipeState.RIGHT,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = fadeIn() + slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth }
            ),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .wrapContentHeight()
            ) {
                rightContent()
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .offset { IntOffset(swipeableState.offset.value.roundToInt(), 0) }
        ) {
            initialContent()
        }
    }
}

@Preview
@Composable
fun AssetItemSwipeBoxPreview() {
    val leftActionBarViewState = ActionBarViewState(
        actionItems = listOf(
            ActionItem(
                iconId = R.drawable.ic_common_send,
                title = stringResource(R.string.common_action_send),
                onClick = {}
            ),
            ActionItem(
                iconId = R.drawable.ic_common_receive,
                title = stringResource(R.string.common_action_receive),
                onClick = {}
            ),
            ActionItem(
                iconId = R.drawable.ic_common_teleport,
                title = stringResource(R.string.common_action_teleport),
                onClick = {}
            )
        )
    )

    val rightActionBarViewState = ActionBarViewState(
        actionItems = listOf(
            ActionItem(
                iconId = R.drawable.ic_common_hide,
                title = stringResource(R.string.common_action_hide),
                onClick = {}
            )
        )
    )

    val assetIconUrl = "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Polkadot.svg"
    val assetChainName = "Karura"
    val assetSymbol = "KSM"
    val assetTokenFiat = "$73.22"
    val assetTokenRate = "+5.67%"
    val assetBalance = "444.3"
    val assetBalanceFiat = "$2345.32"
    val assetChainUrls = listOf(
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Karura.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/kilt.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Moonriver.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Polkadot.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Moonbeam.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Statemine.svg",
        "https://raw.githubusercontent.com/soramitsu/fearless-utils/master/icons/chains/white/Rococo.svg"
    )

    val assetListItemViewState = AssetListItemViewState(
        assetIconUrl = assetIconUrl,
        assetChainName = assetChainName,
        assetSymbol = assetSymbol,
        assetTokenFiat = assetTokenFiat,
        assetTokenRate = assetTokenRate,
        assetBalance = assetBalance,
        assetBalanceFiat = assetBalanceFiat,
        assetChainUrls = assetChainUrls,
        chainId = "",
        chainAssetId = "",
        isSupported = true,
        isHidden = false
    )

    FearlessTheme {
        SwipeBox(
            SwipeBoxViewState(
                leftStateWidth = 250.dp,
                rightStateWidth = 90.dp
            ),
            leftContent = { ActionBar(leftActionBarViewState) },
            rightContent = { ActionBar(rightActionBarViewState) },
            initialContent = { AssetListItem(state = assetListItemViewState, onClick = {}) }
        )
    }
}
