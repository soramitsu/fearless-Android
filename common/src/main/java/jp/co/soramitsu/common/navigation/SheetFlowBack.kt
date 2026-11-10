package jp.co.soramitsu.common.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
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

/**
 * Keeps a sheet open through system Back while [submitting]. Closing it would clear its ViewModel, which cancels the
 * submission running in its scope and drops the result screen. A tap outside still closes the sheet, so a submission
 * that never answers cannot trap the user.
 */
@Composable
fun KeepSheetOpenOnBack(submitting: Boolean) {
    BackHandler(enabled = submitting) { /* Back is spent here; the sheet stays. */ }
}
