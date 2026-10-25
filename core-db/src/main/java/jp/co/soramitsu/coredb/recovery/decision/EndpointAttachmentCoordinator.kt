package jp.co.soramitsu.coredb.recovery.decision

/**
 * Process-local ordering for one authenticated C2 lease. Construction is not authentication.
 * Request and final commit each have a distinct grant; neither is C1/C3 authority.
 * Each grant checks the lease and attempt together under this coordinator's short state monitor.
 * Revocation and abandonment use that same ordering and never wait for network/storage callbacks.
 * If either wins before a grant, its callback cannot run. Already granted callbacks may finish.
 * No callback runs under the monitor, and no grant authorizes late UI publication.
 */
internal class EndpointAttachmentCoordinator {
    private var active = true

    internal class Attempt internal constructor(internal val coordinator: EndpointAttachmentCoordinator) {
        // Every access uses coordinator's monitor, including completion after a suspended request.
        internal var state = NEW
        override fun toString(): String = "EndpointAttachmentAttempt"
    }

    fun newAttempt(): Attempt = synchronized(this) {
        check(active) { "Endpoint attachment authority is revoked" }
        Attempt(this)
    }

    fun revoke() { synchronized(this) { active = false } }
    fun isCurrent(): Boolean = synchronized(this) { active }

    fun abandon(attempt: Attempt) {
        synchronized(this) {
            require(attempt.coordinator === this)
            attempt.state = FINISHED
        }
    }

    /** Non-consuming admission before SQL; final commitOnce must still recheck current MATCH. */
    fun requireMatched(attempt: Attempt) {
        synchronized(this) {
            require(attempt.coordinator === this)
            check(active && attempt.state == MATCHED) { "Endpoint attachment attempt is not currently matched" }
        }
    }

    /** Consume only a fresh observation attempt; this grants neither a request nor a commit. */
    fun grantObservationOnce(attempt: Attempt) {
        synchronized(this) {
            require(attempt.coordinator === this)
            check(attempt.state == NEW) { "Endpoint observation attempt was consumed" }
            attempt.state = FINISHED
            check(active) { "Endpoint attachment authority is revoked" }
        }
    }

    /** Exactly one callback, after one request grant. A false/error/cancelled result consumes it. */
    suspend fun requestOnce(attempt: Attempt, request: suspend () -> Boolean): Boolean =
        requestOrderedOnce(attempt, { grant -> grant() }, request)

    /** The optional outer lane guard encloses only pure state transitions, in lane-to-coordinator order. */
    suspend fun requestOrderedOnce(
        attempt: Attempt,
        order: (() -> Unit) -> Unit,
        request: suspend () -> Boolean
    ): Boolean {
        order {
            synchronized(this) {
                require(attempt.coordinator === this)
                check(attempt.state == NEW) { "Endpoint request attempt was consumed" }
                attempt.state = FINISHED
                check(active) { "Endpoint attachment authority is revoked" }
                attempt.state = REQUESTED
            }
        }
        try {
            val matched = request()
            order {
                synchronized(this) {
                    check(active) { "Endpoint attachment authority is revoked" }
                    check(attempt.state == REQUESTED) { "Endpoint request attempt was abandoned" }
                    attempt.state = if (matched) MATCHED else FINISHED
                }
            }
            return matched
        } catch (failure: Throwable) {
            synchronized(this) { attempt.state = FINISHED }
            throw failure
        }
    }

    /**
     * After caller-owned source/decision/target/occupancy rechecks and DML, grant only transaction end.
     * A failure before this method must roll back the caller's transaction. Physical-commit failure
     * propagates and consumes the attempt. Pure tests prove ordering, never actual SQL rollback.
     */
    fun <T> commitOnce(attempt: Attempt, physicalCommit: () -> T): T {
        synchronized(this) {
            require(attempt.coordinator === this)
            check(attempt.state == MATCHED) { "Endpoint commit requires this invocation's match" }
            attempt.state = FINISHED
            check(active) { "Endpoint attachment authority is revoked" }
            // Linearization point: the final grant is consumed before releasing the state monitor.
        }
        return physicalCommit()
    }

    private companion object {
        const val NEW = 0
        const val REQUESTED = 1
        const val MATCHED = 2
        const val FINISHED = 3
    }
}
