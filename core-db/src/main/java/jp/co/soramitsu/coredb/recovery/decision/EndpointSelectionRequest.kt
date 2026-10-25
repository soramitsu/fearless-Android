package jp.co.soramitsu.coredb.recovery.decision

/** Exact rendered saved decision and attached owner. Neither observation authenticates a new action. */
class EndpointSelectionRequest(val decision: EndpointRecoveryDecision, val owner: EndpointSelectionOwner) {
    override fun toString(): String =
        "EndpointSelectionRequest(decisionId=${decision.id}, revision=${decision.revision})"
}
