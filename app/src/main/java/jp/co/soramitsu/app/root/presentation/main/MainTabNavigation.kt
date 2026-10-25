package jp.co.soramitsu.app.root.presentation.main

import android.os.Bundle
import androidx.annotation.IdRes
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph
import androidx.navigation.NavOptions
import jp.co.soramitsu.app.R

/** NavigationUI-equivalent top-level selection kept explicit so stack semantics are testable. */
internal fun selectMainTab(navController: NavController, @IdRes graphId: Int): Boolean {
    return runCatching {
        selectMainTabOrThrow(navController, graphId)
        true
    }.getOrDefault(false)
}

/** Strict variant for programmatic routes, where silently ignoring a dead route is unsafe. */
internal fun selectMainTabOrThrow(navController: NavController, @IdRes graphId: Int) {
    val options = NavOptions.Builder()
        .setLaunchSingleTop(true)
        .setRestoreState(true)
        .setPopUpTo(
            navController.graph.deepestStartDestinationId(),
            false,
            true
        )
        .build()

    navController.navigate(graphId, null, options)
}

/**
 * Selects the direct child graph that owns [destinationId], then navigates to the requested leaf.
 * AndroidX only resolves destinations in the active graph hierarchy, so attempting to navigate
 * directly to a sibling graph's leaf is otherwise a dead route.
 */
internal fun navigateToMainTabDestination(
    navController: NavController,
    @IdRes destinationId: Int,
    args: Bundle? = null,
    navOptions: NavOptions? = null
) {
    val owner = navController.graph.requireDirectChildOwner(destinationId)

    if (owner is NavGraph) {
        if (destinationId == owner.id) {
            selectMainTabOrThrow(navController, owner.id)
            return
        }

        if (!navController.currentDestination.isInGraph(owner.id)) {
            selectMainTabOrThrow(navController, owner.id)
            check(navController.currentDestination.isInGraph(owner.id)) {
                "Failed to select the main tab that owns destination $destinationId"
            }
        }
    }

    navController.navigate(destinationId, args, navOptions)
}

internal fun rootDestinationForMainTab(@IdRes graphId: Int): Int? = when (graphId) {
    R.id.portfolioGraph -> R.id.walletFragment
    R.id.defiGraph -> R.id.defiHubFragment
    R.id.polkaswapGraph -> R.id.polkaswapHubFragment
    R.id.crossChainGraph -> R.id.crossChainFragment
    R.id.settingsGraph -> R.id.profileFragment
    else -> null
}

internal fun reselectMainTab(navController: NavController, @IdRes graphId: Int): Boolean {
    val rootDestination = rootDestinationForMainTab(graphId) ?: return false
    return navController.popBackStack(rootDestination, inclusive = false)
}

/** Local equivalent of NavigationUI's non-public start-destination traversal. */
private fun NavGraph.deepestStartDestinationId(): Int {
    var destination: NavDestination = this
    while (destination is NavGraph) {
        destination = destination.findNode(destination.startDestinationId) ?: break
    }
    return destination.id
}

/** Returns the root graph for global children, or the owning direct child graph for tab leaves. */
private fun NavGraph.requireDirectChildOwner(@IdRes destinationId: Int): NavDestination {
    if (id == destinationId) return this

    for (directChild in this) {
        if (directChild.id == destinationId) return directChild
        if (directChild is NavGraph && directChild.containsRecursively(destinationId)) {
            return directChild
        }
    }

    throw IllegalArgumentException(
        "Destination $destinationId is not owned by the authenticated navigation graph"
    )
}

private fun NavGraph.containsRecursively(@IdRes destinationId: Int): Boolean {
    if (id == destinationId) return true
    return any { child ->
        child.id == destinationId ||
            (child is NavGraph && child.containsRecursively(destinationId))
    }
}

private fun NavDestination?.isInGraph(@IdRes graphId: Int): Boolean {
    var destination = this
    while (destination != null) {
        if (destination.id == graphId) return true
        destination = destination.parent
    }
    return false
}
