package jp.co.soramitsu.polkaswap.impl.presentation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import jp.co.soramitsu.polkaswap.impl.presentation.SwapDestinationsArgs.CHAIN_ID
import jp.co.soramitsu.polkaswap.impl.presentation.SwapDestinationsArgs.TOKEN_FROM_ID
import jp.co.soramitsu.polkaswap.impl.presentation.SwapDestinationsArgs.TOKEN_TO_ID
import jp.co.soramitsu.polkaswap.impl.presentation.SwapScreens.SWAP_CONFIRM_SCREEN
import jp.co.soramitsu.polkaswap.impl.presentation.SwapScreens.SWAP_SCREEN

/**
 * Screens used in [SwapDestinations]
 */
private object SwapScreens {
    const val SWAP_SCREEN = "swap"
    const val SWAP_CONFIRM_SCREEN = "confirm"
}

/**
 * Arguments used in [SwapDestinations] routes
 */
object SwapDestinationsArgs {
    const val CHAIN_ID = "chainId"
    const val TOKEN_FROM_ID = "tokenFromId"
    const val TOKEN_TO_ID = "tokenToId"
}

/**
 * Destinations used in the [SwapFlowFragment]
 */
object SwapDestinations {
//    const val LOADING = "Loading"
    const val SWAP_ROUTE = "$SWAP_SCREEN/?$CHAIN_ID={$CHAIN_ID}&$TOKEN_FROM_ID={$TOKEN_FROM_ID}&$TOKEN_TO_ID={$TOKEN_TO_ID}"
    const val SWAP_CONFIRM_ROUTE = "$SWAP_CONFIRM_SCREEN/$CHAIN_ID/$TOKEN_FROM_ID/$TOKEN_TO_ID"
}

/**
 * Models the navigation actions in the app.
 */
class SwapNavigationActions(private val navController: NavHostController) {

    fun navigateToSwap(chainId: String?, assetIdFrom: String?, assetIdTo: String?) {
        println("!!! navigateToSwap: chainId = $chainId; assetIdFrom = $assetIdFrom; assetIdTo = $assetIdTo")
        navController.navigate(
            "$SWAP_SCREEN/?$CHAIN_ID={$chainId}&$TOKEN_FROM_ID={$assetIdFrom}&$TOKEN_TO_ID={$assetIdTo}"
        ) {
            popUpTo(navController.graph.findStartDestination().id) {
//                inclusive = !navigatesFromDrawer
//                saveState = navigatesFromDrawer
            }
            launchSingleTop = true
//            restoreState = navigatesFromDrawer
        }
    }
}
