package jp.co.soramitsu.coredb.recovery.decision

/** Bounded local choices; a page never grants permission to contact or select a node. */
class EndpointDecisionTargetPage internal constructor(
    options: List<EndpointDecisionTargetOption>,
    val previousBeforeRowId: Long?,
    val nextAfterRowId: Long?
) {
    val options: List<EndpointDecisionTargetOption> = options.toList()
    init {
        require(options.size <= EndpointDecisionTargetCatalog.MAXIMUM_PAGE_SIZE)
        require(options.zipWithNext().all { (a, b) -> a.rowId < b.rowId })
        require(options.isNotEmpty() || (previousBeforeRowId == null && nextAfterRowId == null))
        require(previousBeforeRowId == null || previousBeforeRowId == options.firstOrNull()?.rowId)
        require(nextAfterRowId == null || nextAfterRowId == options.lastOrNull()?.rowId)
    }

    override fun toString(): String = "EndpointDecisionTargetPage(count=${options.size})"
}
