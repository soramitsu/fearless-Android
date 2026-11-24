package jp.co.soramitsu.app.root.navigation

/**
 * Resolves authenticated routes without ever escaping a mounted shell.
 *
 * The parent lookup is deliberately lazy: when [shellController] exists, a missing child resolves
 * to `null` and the parent is never consulted. This prevents route drift from replacing
 * MainFragment and hiding the persistent tabs.
 *
 * A child the shell owns goes to the parent only when [opensOverParentSheet] says it would open as a
 * sheet over a sheet in the parent's stack. A sheet replaces nothing and the tabs stay mounted under it,
 * while in the shell's stack it would sit over a sheet that Back and results do not know is covered.
 */
internal inline fun <T> authenticatedDestinationTarget(
    shellController: T?,
    shellContainsDestination: (T) -> Boolean,
    parentController: () -> T?,
    opensOverParentSheet: (T) -> Boolean = { false }
): T? {
    if (shellController != null) {
        if (!shellContainsDestination(shellController)) return null
        return parentController()?.takeIf(opensOverParentSheet) ?: shellController
    }

    return parentController()
}
