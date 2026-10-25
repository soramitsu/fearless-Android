package jp.co.soramitsu.coredb.recovery.decision

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-local final commit ordering for one already authenticated authority.
 *
 * This class does not authenticate a caller. A future trusted adapter must create
 * one instance for exactly one claimed owner/lease and revoke it whenever that
 * authority ends. It must never replace a revoked instance without fresh approval.
 *
 * Source hashing, SQLite lock acquisition, all transaction checks and decision
 * DML precede [commitOnce]. The successful active-state CAS is the authorization
 * linearization point. Revocation before it rejects the commit; revocation after
 * it returns immediately while the authorized physical commit may still finish.
 * There is deliberately no shared monitor around storage work.
 *
 * Nothing here grants UI publication, RPC, attachment or selection authority.
 * The future adapter must check its exact lease on the main thread immediately
 * before publishing, with no suspension between that check and the UI update.
 *
 * Isolated C1 proposal only: no production authority adapter is wired yet.
 */
internal class EndpointDecisionCommitCoordinator {
    private val active = AtomicBoolean(true)

    /** Opaque and single-use; neither this object nor its coordinator is persisted. */
    internal class Attempt internal constructor(
        internal val coordinator: EndpointDecisionCommitCoordinator
    ) {
        internal val consumed = AtomicBoolean(false)

        override fun toString(): String = "EndpointDecisionCommitAttempt"
    }

    /** Starts one operation; revocation immediately afterwards still denies its final grant. */
    fun newAttempt(): Attempt {
        check(active.get()) { "Endpoint decision authority is revoked" }
        return Attempt(this)
    }

    /** Revokes new grants without waiting for a worker, SQLite, or any callback. */
    fun revoke() {
        active.set(false)
    }

    /** A current-authority snapshot only; this is not a UI publication fence. */
    fun isCurrent(): Boolean = active.get()

    /** Closes an operation that failed before its final grant; later replay is rejected. */
    fun abandon(attempt: Attempt) {
        require(attempt.coordinator === this) { "Endpoint decision authority mismatch" }
        attempt.consumed.set(true)
    }

    /**
     * Enters [physicalCommit] once, immediately after the final atomic grant.
     *
     * The caller owns the SQLite transaction and must roll it back if this method
     * rejects. The callback must contain only the final successful transaction end,
     * with no source reads, hashing, DML, network call or UI work. A callback error
     * propagates unchanged and consumes the attempt; it never grants a retry.
     */
    fun <T> commitOnce(attempt: Attempt, physicalCommit: () -> T): T {
        require(attempt.coordinator === this) { "Endpoint decision authority mismatch" }
        check(attempt.consumed.compareAndSet(false, true)) { "Endpoint decision attempt was consumed" }

        // A strong self-CAS and revoke's write share one atomic modification order.
        // No grant token escapes for deferred use or replay.
        check(active.compareAndSet(true, true)) { "Endpoint decision authority is revoked" }
        return physicalCommit()
    }
}
