package jp.co.soramitsu.coredb.recovery.decision

import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane

/** Acknowledged durable selection after transaction end. No transport, connectivity or verification claim. */
class EndpointSelectionResult internal constructor(val kind: Kind, private val capture: EndpointSelectionCapture) {
    enum class Kind { SELECTED, ALREADY_SELECTED }

    val decision: EndpointRecoveryDecision get() = capture.request.decision
    val owner: EndpointSelectionOwner get() = capture.request.owner
    val command: EndpointMutationLane.Operation get() = capture.operation
    override fun toString(): String = "EndpointSelectionResult(kind=$kind, decisionId=${decision.id})"
}
