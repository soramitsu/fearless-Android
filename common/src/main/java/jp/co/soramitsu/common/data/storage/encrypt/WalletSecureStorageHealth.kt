package jp.co.soramitsu.common.data.storage.encrypt

/**
 * Process-wide fail-closed signal for ambiguous secure-storage durability.
 *
 * A failed synchronous preference commit may have changed only the process
 * memory view, only the disk view, or both. No encrypted preference reader or
 * startup fast path may make another cross-store decision after that point.
 * The state is therefore monotonic for the lifetime of the process; only a new
 * process may safely inspect the last durable snapshot and reconcile it.
 */
object WalletSecureStorageHealth {

    private val monitor = Any()
    private val restartRequiredListeners = linkedSetOf<() -> Unit>()

    @Volatile
    private var processRestartRequired = false

    fun isHealthy(): Boolean = !processRestartRequired

    fun requireHealthy() {
        if (processRestartRequired) {
            throw WalletSecureStorageUnavailableException(
                "Secure-storage durability is ambiguous until the process restarts",
                kind = WalletSecureStorageFailureKind.PROCESS_RESTART_REQUIRED
            )
        }
    }

    /**
     * Registers a process-lifetime observer.
     *
     * Registration and latching share [monitor], so a concurrent registration
     * observes the transition exactly once: either in the latch snapshot or as
     * an immediate callback after registration. Callbacks run outside the
     * monitor and cannot delay other health checks.
     */
    fun addProcessRestartRequiredListener(
        listener: () -> Unit
    ): () -> Unit {
        val notifyImmediately = synchronized(monitor) {
            restartRequiredListeners += listener
            processRestartRequired
        }
        if (notifyImmediately) {
            notifySafely(listener)
        }

        return {
            synchronized(monitor) {
                restartRequiredListeners -= listener
            }
        }
    }

    fun latchProcessRestartRequired() {
        val listeners = synchronized(monitor) {
            if (processRestartRequired) return

            processRestartRequired = true
            restartRequiredListeners.toList()
        }

        listeners.forEach(::notifySafely)
    }

    /**
     * Test-only process-death simulation. Production code has no reset API.
     */
    internal fun resetForTest() {
        synchronized(monitor) {
            processRestartRequired = false
        }
    }

    private fun notifySafely(listener: () -> Unit) {
        try {
            listener()
        } catch (_: Throwable) {
            // The health transition is security-critical and already latched.
            // A broken observer must not replace the typed storage failure or
            // prevent other observers from invalidating their ready state.
        }
    }
}
