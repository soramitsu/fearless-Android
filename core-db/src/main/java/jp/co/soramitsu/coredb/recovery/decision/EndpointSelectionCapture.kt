package jp.co.soramitsu.coredb.recovery.decision

import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane

/** A bounded SQL observation bound to one process command; never reusable authorization. */
class EndpointSelectionCapture internal constructor(
    val request: EndpointSelectionRequest,
    internal val operation: EndpointMutationLane.Operation,
    internal val targetRowId: Long,
    internal val selected: EndpointSelectionSql.Selected?
) {
    val alreadySelected: Boolean get() = selected?.rowId == targetRowId

    /** Identity check only; exposes no row data or grant and never creates authority. */
    fun belongsTo(command: EndpointMutationLane.Operation): Boolean = operation === command

    override fun toString(): String = "EndpointSelectionCapture(decisionId=${request.decision.id})"
}
