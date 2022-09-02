package jp.co.soramitsu.account.impl.presentation.about

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.rememberSwipeableState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import jp.co.soramitsu.common.compose.component.ActionBar
import jp.co.soramitsu.common.compose.component.ActionBarViewState
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.AssetListItem
import jp.co.soramitsu.common.compose.component.SwipeBox
import jp.co.soramitsu.common.compose.component.SwipeBoxViewState
import jp.co.soramitsu.common.compose.component.SwipeState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.common.presentation.LoadingState

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AboutScreen(
    viewModel: AboutViewModel = hiltViewModel()
) {
    val state = viewModel.uiState.value

    when (state) {
        is LoadingState.Loading -> {}
        is LoadingState.Loaded -> {
            state.data
        }
    }

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

    FearlessTheme {
        SwipeBox(
            swipeableState = rememberSwipeableState(SwipeState.INITIAL),
            state = SwipeBoxViewState(
                leftStateWidth = 250.dp,
                rightStateWidth = 90.dp
            ),
            leftContent = { ActionBar(leftActionBarViewState) },
            rightContent = { ActionBar(rightActionBarViewState) },
            initialContent = { AssetListItem(state = assetListItemViewState, onClick = {}) }
        )
    }
}

@Preview
@Composable
private fun PreviewAboutScreen() {
    FearlessTheme {
        AboutScreen()
    }
}
