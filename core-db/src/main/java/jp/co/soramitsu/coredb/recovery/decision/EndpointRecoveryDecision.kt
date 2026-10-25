package jp.co.soramitsu.coredb.recovery.decision

/** Persisted offline intent only. Pending never means verified, attached, active, or authorized. */
class EndpointRecoveryDecision internal constructor(
    val id: Long,
    val revision: Long,
    val source: EndpointDecisionSourceBinding,
    val candidate: EndpointDecisionCandidate,
    val target: EndpointDecisionTarget
) {
    init { require(id > 0 && revision > 0) }
    val state: String get() = "PENDING"
    override fun toString(): String = "EndpointRecoveryDecision(id=$id, revision=$revision, state=PENDING)"
}
