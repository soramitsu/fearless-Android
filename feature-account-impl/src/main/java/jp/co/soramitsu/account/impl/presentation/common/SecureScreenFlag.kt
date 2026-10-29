package jp.co.soramitsu.account.impl.presentation.common

/** The FLAG_SECURE bit of one window, abstracted so the policy is JVM-testable. */
internal interface SecureFlagWindow {
    val isSecure: Boolean

    fun addSecureFlag()

    fun clearSecureFlag()
}

/**
 * Keeps FLAG_SECURE on while a screen that shows a recovery phrase, seed or
 * keystore JSON is visible, then restores the state the window had before.
 *
 * Neighbouring screens (PIN, recovery import, exports, archived keys) set the
 * flag too, and a fragment transition creates the incoming view before it
 * destroys the outgoing one. Recording and restoring alone would therefore let
 * the PIN screen clear the flag under an export screen opened from it, or let
 * an export screen clear it under the PIN screen it opens. This policy, which
 * every one of those screens follows, instead:
 *  - re-applies the flag when a screen that left after this one appeared
 *    restored "not secure" ([onOtherViewDestroyed]);
 *  - when this screen is being removed, restores the earlier state before the
 *    incoming screen records it ([onLeaving]), then either hands the flag to
 *    an incoming screen that set it itself or keeps covering this screen's
 *    exit animation until its view is gone ([onIncomingShown], [hide]).
 */
internal class SecureScreenFlag {

    private var window: SecureFlagWindow? = null
    private var secureBefore = false
    private var leaving = false

    /** The screen's view exists or the screen started; idempotent. */
    fun show(target: SecureFlagWindow) {
        if (window == target) {
            leaving = false
            target.addSecureFlag()
            return
        }

        hide()
        window = target
        secureBefore = target.isSecure
        leaving = false
        target.addSecureFlag()
    }

    /** The screen stopped because it is being replaced or popped. */
    fun onLeaving() {
        val current = window ?: return
        if (leaving) return

        leaving = true
        if (!secureBefore) current.clearSecureFlag()
    }

    /** Another screen in the same container created its view or started. */
    fun onIncomingShown() {
        val current = window ?: return
        if (!leaving) return

        leaving = false
        if (current.isSecure) {
            // The incoming screen set the flag itself and clears it when it
            // leaves; clearing it once this view is gone would expose it.
            secureBefore = true
        } else {
            current.addSecureFlag()
        }
    }

    /** Another screen in the same container destroyed its view. */
    fun onOtherViewDestroyed() {
        val current = window ?: return
        if (leaving || current.isSecure) return

        // A screen that left after this one appeared restored "not secure".
        secureBefore = false
        current.addSecureFlag()
    }

    /** The screen's view is destroyed; idempotent. */
    fun hide() {
        val current = window ?: return

        window = null
        leaving = false
        if (!secureBefore) current.clearSecureFlag()
    }
}
