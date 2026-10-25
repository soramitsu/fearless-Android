package jp.co.soramitsu.coredb.recovery.decision

/** Current storage observation only. No result authorizes a future probe, attachment or selection. */
class EndpointAttachmentResult internal constructor(
    val kind: Kind,
    val ownerId: Long,
    val ownerRevision: Long,
    val isActive: Boolean,
    val matchedThisInvocation: Boolean,
    val requiresSourceRefresh: Boolean
) {
    enum class Kind { ADDED_INACTIVE, REATTACHED_INACTIVE, ALREADY_PRESENT }
    init {
        require(ownerRevision > 0)
        require(kind == Kind.ALREADY_PRESENT || (!isActive && matchedThisInvocation))
        require(requiresSourceRefresh == (kind == Kind.REATTACHED_INACTIVE))
    }
    override fun toString(): String = "EndpointAttachmentResult(kind=$kind, ownerId=$ownerId, revision=$ownerRevision)"
}
