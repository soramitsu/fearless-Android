package jp.co.soramitsu.coredb.recovery.decision

/**
 * Trusted adapter-owned authority for exactly one current authenticated C2 lease.
 * Construction does not authenticate. Keep separate from offline-save and selection grants.
 * No callback runs under a lifecycle monitor; grants are process-local and one-use.
 */
class EndpointAttachmentAuthority {
    private val coordinator = EndpointAttachmentCoordinator()

    /** An opaque invocation bound to the exact prepared immutable capture identity. */
    class Attempt internal constructor(
        internal val authority: EndpointAttachmentAuthority,
        internal val capture: EndpointAttachmentCapture,
        internal val token: EndpointAttachmentCoordinator.Attempt
    ) {
        override fun toString(): String = "EndpointAttachmentAttempt"
    }

    /** Admit one explicit invocation. This alone grants no request or write. */
    fun newAttempt(capture: EndpointAttachmentCapture): Attempt = Attempt(this, capture, coordinator.newAttempt())

    /** Synchronously prevent future grants without waiting for network or storage workers. */
    fun revoke() = coordinator.revoke()

    /** Snapshot admission only; final main-thread publication must recheck exact lease/operation. */
    fun isCurrent(): Boolean = coordinator.isCurrent()

    /** The trusted adapter supplies only its one actual bounded probe and exact MATCH comparison. */
    suspend fun requestOnce(attempt: Attempt, request: suspend () -> Boolean): Boolean {
        require(attempt.authority === this)
        val operation = attempt.capture.mutation
        return if (operation == null) coordinator.requestOnce(attempt.token, request) else
            coordinator.requestOrderedOnce(attempt.token, { grant -> operation.lane.withCurrent(operation, grant) }, request)
    }

    /** Consume failure/cancellation or a finished observation; no retry authority is retained. */
    fun abandon(attempt: Attempt) {
        require(attempt.authority === this)
        coordinator.abandon(attempt.token)
    }

    internal fun requireMatched(attempt: Attempt, capture: EndpointAttachmentCapture) {
        requireBinding(attempt, capture)
        coordinator.requireMatched(attempt.token)
    }

    internal fun <T> commitOnce(attempt: Attempt, capture: EndpointAttachmentCapture, commit: () -> T): T {
        requireBinding(attempt, capture)
        return coordinator.commitOnce(attempt.token, commit)
    }

    internal fun grantObservationOnce(attempt: Attempt, capture: EndpointAttachmentCapture) {
        requireBinding(attempt, capture)
        require(capture.alreadyPresent)
        coordinator.grantObservationOnce(attempt.token)
    }

    internal fun requireBinding(attempt: Attempt, capture: EndpointAttachmentCapture) {
        require(attempt.authority === this && attempt.capture === capture)
    }
}
