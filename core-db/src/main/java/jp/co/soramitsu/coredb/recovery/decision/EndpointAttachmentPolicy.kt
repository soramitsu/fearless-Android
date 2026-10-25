package jp.co.soramitsu.coredb.recovery.decision

/** Pure classification of bounded scalar observations; never treats presence as verification. */
internal object EndpointAttachmentPolicy {
    enum class Action { ADD_NEW, REATTACH_EXACT_OWNED }
    enum class Kind { INSERT_INACTIVE, REATTACH_INACTIVE, ALREADY_INACTIVE, ALREADY_ACTIVE }

    /** No raw TEXT crosses this boundary; SQL compares admitted candidate bytes exactly. */
    data class Owner(
        val id: Long,
        val revision: Long,
        val attached: Long,
        val deleted: Long,
        val payloadMatches: Boolean
    )

    data class Projection(val isDefault: Long, val isActive: Long, val payloadMatches: Boolean)
    data class Plan(val kind: Kind, val ownerId: Long?, val nextRevision: Long?)

    /** Invalid metadata, competing ownership and implicit tombstone restoration always reject. */
    fun plan(
        action: Action,
        owner: Owner?,
        projection: Projection?,
        ownedSourceId: Long?,
        ownedSourceRevision: Long?
    ): Plan {
        require((ownedSourceId == null) == (ownedSourceRevision == null))
        ownedSourceRevision?.let { require(it > 0) }
        owner?.let {
            check(it.revision > 0 && it.attached in 0L..1L && it.deleted in 0L..1L)
            check(it.attached == 0L || it.deleted == 0L)
        }
        projection?.let { check(it.isDefault in 0L..1L && it.isActive in 0L..1L) }
        if (owner == null) {
            check(projection == null && action == Action.ADD_NEW) { "Endpoint key is unavailable" }
            return Plan(Kind.INSERT_INACTIVE, null, 1)
        }
        check(owner.deleted == 0L && owner.payloadMatches) { "Endpoint key is reserved" }
        if (owner.attached == 1L) {
            check(projection != null && projection.isDefault == 0L && projection.payloadMatches) {
                "Endpoint projection differs"
            }
            return Plan(if (projection.isActive == 1L) Kind.ALREADY_ACTIVE else Kind.ALREADY_INACTIVE, owner.id, null)
        }
        check(projection == null && action == Action.REATTACH_EXACT_OWNED &&
            owner.id == ownedSourceId && owner.revision == ownedSourceRevision) { "Endpoint ownership differs" }
        check(owner.revision < Long.MAX_VALUE) { "Endpoint revision cannot advance" }
        return Plan(Kind.REATTACH_INACTIVE, owner.id, owner.revision + 1)
    }
}
