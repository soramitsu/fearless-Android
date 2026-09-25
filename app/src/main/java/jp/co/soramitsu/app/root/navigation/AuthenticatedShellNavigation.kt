package jp.co.soramitsu.app.root.navigation

/**
 * Resolves authenticated routes without ever escaping a mounted shell.
 *
 * The parent lookup is deliberately lazy: when [shellController] exists, a missing child resolves
 * to `null` and the parent is never consulted. This prevents route drift from replacing
 * MainFragment and hiding the persistent tabs.
 */
internal inline fun <T> authenticatedDestinationTarget(
    shellController: T?,
    shellContainsDestination: (T) -> Boolean,
    parentController: () -> T?
): T? {
    if (shellController != null) {
        return shellController.takeIf(shellContainsDestination)
    }

    return parentController()
}
