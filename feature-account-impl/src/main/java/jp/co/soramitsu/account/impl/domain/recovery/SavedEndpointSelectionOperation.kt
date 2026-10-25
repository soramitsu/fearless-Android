package jp.co.soramitsu.account.impl.domain.recovery

import jp.co.soramitsu.coredb.recovery.decision.EndpointSelectionAuthority
import jp.co.soramitsu.coredb.recovery.decision.EndpointSelectionCapture
import jp.co.soramitsu.coredb.recovery.decision.EndpointSelectionRequest
import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane

/** One explicit Select tap. Neither its payload nor cancellation is serialized or replayed. */
internal class SavedEndpointSelectionOperation(
    internal val owner: Any,
    internal val lease: SavedEndpointAccess.Lease,
    internal val authority: EndpointSelectionAuthority,
    internal val request: EndpointSelectionRequest,
    private val lane: EndpointMutationLane,
    internal val mutation: EndpointMutationLane.Operation
) {
    private var active = true
    private var started = false
    private var published = false
    private var attempt: EndpointSelectionAuthority.Attempt? = null

    /** Short operation→lane order only; no SQL, physical commit or callback under this monitor. */
    fun cancel() {
        val currentAttempt = synchronized(this) {
            active = false
            attempt
        }
        currentAttempt?.let(authority::abandon)
        lane.abandon(mutation)
    }

    internal fun begin(): Boolean = synchronized(this) {
        if (!active || started) {
            false
        } else {
            started = true

            true
        }
    }

    internal fun bind(capture: EndpointSelectionCapture): EndpointSelectionAuthority.Attempt = synchronized(this) {
        check(active && started && attempt == null)
        check(capture.request === request && capture.belongsTo(mutation))
        authority.newAttempt(capture).also { attempt = it }
    }

    internal fun grantPublication(): Boolean = synchronized(this) {
        if (!active || !started || published) {
            false
        } else {
            published = true

            true
        }
    }

    override fun toString(): String = "SavedEndpointSelectionOperation"
}
