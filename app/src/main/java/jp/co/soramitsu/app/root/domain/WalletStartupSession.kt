package jp.co.soramitsu.app.root.domain

import androidx.annotation.VisibleForTesting

/**
 * Process-local proof that the secure-storage and Room gate completed.
 *
 * Android may restore the persisted heavy activity directly after process
 * death or an app upgrade. This value intentionally resets with the process,
 * forcing that restored activity back through the lightweight startup gate
 * before any Fragment, ViewModel, or navigation graph can be recreated.
 */
internal object WalletStartupSession {

    private data class State(
        val check: WalletStartupCheck?,
        val ready: Boolean
    )

    @Volatile
    private var state = State(check = null, ready = false)

    /**
     * Starts a check only when this process has not already completed one.
     *
     * Launcher and deep-link entry points may be delivered while the heavy
     * wallet activity is already alive. Revoking a successful process-local
     * proof in that case would leave the existing database consumers running
     * behind a newly failing gate. The admission decision and state change
     * therefore have to be one synchronized operation.
     *
     * A null result means the caller should forward its intent to the existing
     * ready wallet without reopening Room or secure storage.
     */
    @Synchronized
    fun beginCheckUnlessReady(): WalletStartupCheck? {
        if (state.ready) return null

        return WalletStartupCheck().also { check ->
            state = State(check = check, ready = false)
        }
    }

    /**
     * Marks the process ready only when [check] is still the newest pending
     * startup check. The false result also suppresses duplicate callbacks.
     */
    @Synchronized
    fun markReady(check: WalletStartupCheck): Boolean {
        val current = state
        if (current.check !== check || current.ready) return false

        state = current.copy(ready = true)
        return true
    }

    fun isPending(check: WalletStartupCheck): Boolean {
        val current = state
        return current.check === check && !current.ready
    }

    fun isReady(): Boolean = state.ready

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    @Synchronized
    internal fun resetForTest() {
        state = State(check = null, ready = false)
    }
}

internal class WalletStartupCheck
