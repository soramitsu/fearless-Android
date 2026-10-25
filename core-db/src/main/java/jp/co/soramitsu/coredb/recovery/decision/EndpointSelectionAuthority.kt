package jp.co.soramitsu.coredb.recovery.decision

import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane

/**
 * Trusted adapter-owned C3 authority for one non-restored authenticated lease.
 * Construction is not authentication. C1 and C2 authorities cannot supply these grants.
 * Revocation, intent replacement and final grant share one short monitor, never SQL or callbacks.
 */
class EndpointSelectionAuthority(private val lane: EndpointMutationLane) {
    private var active = true

    /** Identity-bound one-use invocation; payload and credentials are excluded from diagnostics. */
    class Attempt internal constructor(
        internal val authority: EndpointSelectionAuthority,
        internal val capture: EndpointSelectionCapture
    ) {
        internal var consumed = false
        override fun toString(): String = "EndpointSelectionAttempt"
    }

    fun newAttempt(capture: EndpointSelectionCapture): Attempt = synchronized(lane.stateLock) {
        check(active) { "Endpoint selection authority is revoked" }
        lane.requireCurrentLocked(capture.operation)
        Attempt(this, capture)
    }

    fun revoke() { synchronized(lane.stateLock) { active = false } }
    fun isCurrent(): Boolean = synchronized(lane.stateLock) { active }

    fun abandon(attempt: Attempt) {
        synchronized(lane.stateLock) {
            require(attempt.authority === this)
            attempt.consumed = true
        }
        lane.abandon(attempt.capture.operation)
    }

    internal fun requireCurrent(attempt: Attempt, capture: EndpointSelectionCapture) {
        synchronized(lane.stateLock) {
            requireBinding(attempt, capture)
            check(active && !attempt.consumed) { "Endpoint selection authority is unavailable" }
            lane.requireCurrentLocked(capture.operation)
        }
    }

    internal fun <T> commitOnce(
        attempt: Attempt,
        capture: EndpointSelectionCapture,
        physicalCommit: () -> T
    ): T {
        requireBinding(attempt, capture)
        lane.grantFinal(capture.operation) {
            check(!attempt.consumed) { "Endpoint selection attempt was consumed" }
            attempt.consumed = true
            check(active) { "Endpoint selection authority is revoked" }
        }
        return physicalCommit()
    }

    private fun requireBinding(attempt: Attempt, capture: EndpointSelectionCapture) {
        require(attempt.authority === this && attempt.capture === capture && capture.operation.lane === lane)
    }
}
