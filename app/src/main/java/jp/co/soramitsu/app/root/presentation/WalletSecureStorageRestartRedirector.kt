package jp.co.soramitsu.app.root.presentation

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Bridges the process-wide secure-storage durability latch into an Activity.
 *
 * The latch may be raised by an IO coroutine while the host is foregrounded,
 * stopped, being recreated, or already tearing down. The redirect is therefore
 * posted to the main thread, deferred until the host is started, and allowed to
 * run at most once. A callback already snapshotted by the health registry is
 * harmless after [unregister].
 */
internal class WalletSecureStorageRestartRedirector(
    private val addRestartRequiredListener: ((() -> Unit) -> () -> Unit),
    private val postToMain: (() -> Unit) -> Unit,
    private val canRedirectNow: () -> Boolean,
    private val redirectToStartup: () -> Unit
) {

    private val active = AtomicBoolean(false)
    private val restartRequired = AtomicBoolean(false)
    private val redirectStarted = AtomicBoolean(false)
    private val removeListener = AtomicReference<(() -> Unit)?>(null)

    fun register() {
        if (!active.compareAndSet(false, true)) return

        val remove = addRestartRequiredListener {
            restartRequired.set(true)
            postToMain(::redirectIfPossible)
        }
        check(removeListener.compareAndSet(null, remove)) {
            "A secure-storage restart listener is already registered"
        }

        // unregister() may race the listener registrar returning. If teardown
        // already won, remove the newly returned listener instead of leaking an
        // Activity reference for the lifetime of the process.
        if (!active.get()) {
            removeListener.getAndSet(null)?.invoke()
        }
    }

    fun onHostStarted() {
        redirectIfPossible()
    }

    fun unregister() {
        if (!active.compareAndSet(true, false)) return
        removeListener.getAndSet(null)?.invoke()
    }

    private fun redirectIfPossible() {
        if (!active.get() || !restartRequired.get() || !canRedirectNow()) {
            return
        }
        if (redirectStarted.compareAndSet(false, true)) {
            redirectToStartup()
        }
    }
}
