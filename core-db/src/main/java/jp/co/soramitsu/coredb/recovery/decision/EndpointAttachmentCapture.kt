package jp.co.soramitsu.coredb.recovery.decision

import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane

/** Exact bounded saved intent captured in one SQL snapshot; not authorization or verification. */
class EndpointAttachmentCapture internal constructor(
    val decision: EndpointRecoveryDecision,
    val action: EndpointAttachmentAction,
    internal val owner: EndpointAttachmentPolicy.Owner?,
    internal val ownerOriginRowId: Long?,
    internal val projection: EndpointAttachmentPolicy.Projection?,
    internal val plan: EndpointAttachmentPolicy.Plan,
    internal val mutation: EndpointMutationLane.Operation? = null
) {
    /** True only for current exact owned/projection presence; does not claim how it was created. */
    val alreadyPresent: Boolean get() = plan.kind in setOf(
        EndpointAttachmentPolicy.Kind.ALREADY_ACTIVE, EndpointAttachmentPolicy.Kind.ALREADY_INACTIVE
    )
    override fun toString(): String = "EndpointAttachmentCapture(decisionId=${decision.id}, revision=${decision.revision})"
}
