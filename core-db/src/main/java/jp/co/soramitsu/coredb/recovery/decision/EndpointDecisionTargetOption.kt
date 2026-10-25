package jp.co.soramitsu.coredb.recovery.decision

/** One bounded catalog row. An unavailable identity remains visible but cannot be chosen. */
class EndpointDecisionTargetOption internal constructor(
    val rowId: Long,
    val name: String?,
    val target: EndpointDecisionTarget?
) {
    override fun toString(): String = "EndpointDecisionTargetOption(rowId=$rowId, available=${target != null})"
}
