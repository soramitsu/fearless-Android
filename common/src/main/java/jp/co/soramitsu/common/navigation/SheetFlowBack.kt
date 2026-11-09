package jp.co.soramitsu.common.navigation

import androidx.navigation.NavController

/**
 * One Back in a bottom sheet flow whose NavHost starts on a Loading placeholder: shows the page
 * underneath or, from the flow's first page, leaves the flow. The placeholder is never shown again.
 */
fun NavController.popFlowPageOrExit(loadingRoute: String, exitFlow: () -> Unit) {
    val previousRoute = previousBackStackEntry?.destination?.route
    if (previousRoute == null || previousRoute == loadingRoute) {
        exitFlow()
    } else {
        popBackStack()
    }
}
