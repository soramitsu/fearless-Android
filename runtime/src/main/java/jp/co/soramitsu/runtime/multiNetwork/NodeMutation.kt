package jp.co.soramitsu.runtime.multiNetwork

import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane

/**
 * One explicit node action, admitted before any queued coroutine or endpoint check.
 * It is process-local ordering, not saved-endpoint authentication or durable authority.
 */
class NodeMutation internal constructor(
    val chainId: String,
    internal val lane: EndpointMutationLane,
    internal val operation: EndpointMutationLane.Operation
) {
    @Volatile
    private var cancelled = false

    /** A committed result is publishable only while this command remains latest and open. */
    val isLatest: Boolean
        get() = !cancelled && lane.isLatest(operation)

    /** Cancels waiting/prepared work without waiting for SQL or an already granted commit. */
    fun cancel() {
        cancelled = true
        lane.abandon(operation)
    }

    internal fun requireCurrent(expectedChainId: String, expectedLane: EndpointMutationLane) {
        require(chainId == expectedChainId && lane === expectedLane) { "Node mutation target differs" }
        check(!cancelled && lane.isCurrent(operation)) { "Node mutation was superseded or cancelled" }
    }

    override fun toString(): String = "NodeMutation"
}
